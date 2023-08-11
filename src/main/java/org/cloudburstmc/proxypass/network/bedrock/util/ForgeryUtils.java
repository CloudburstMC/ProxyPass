package org.cloudburstmc.proxypass.network.bedrock.util;

import lombok.experimental.UtilityClass;
import net.raphimc.mcauth.step.bedrock.StepMCChain;
import org.cloudburstmc.proxypass.network.bedrock.session.Account;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class ForgeryUtils {
    private static final String MOJANG_PUBLIC_KEY = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8ELkixyLcwlZryUQcu1TvPOmI2B7vX83ndnWRUaXm74wFfa5f/lwQNTfrLVHa2PmenpGI6JhIMUJaWZrjmMj90NoKNFSNBuKdm8rYiXsfaz3K36x/1U26HpG0ZxK/V1V";

    public static String forgeOfflineAuthData(KeyPair pair, JSONObject extraData) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        long timestamp = System.currentTimeMillis();
        Date nbf = new Date(timestamp - TimeUnit.SECONDS.toMillis(1));
        Date exp = new Date(timestamp + TimeUnit.DAYS.toMillis(1));

        JwtClaims claimsSet = new JwtClaims();
        claimsSet.setNotBefore(NumericDate.fromMilliseconds(nbf.getTime()));
        claimsSet.setExpirationTime(NumericDate.fromMilliseconds(exp.getTime()));
        claimsSet.setIssuedAt(NumericDate.fromMilliseconds(exp.getTime()));
        claimsSet.setIssuer("self");
        claimsSet.setClaim("certificateAuthority", true);
        claimsSet.setClaim("extraData", extraData);
        claimsSet.setClaim("identityPublicKey", publicKeyBase64);

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claimsSet.toJson());
        jws.setKey(pair.getPrivate());
        jws.setAlgorithmHeaderValue("ES384");
        jws.setHeader(HeaderParameterNames.X509_URL, publicKeyBase64);

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<String> forgeOnlineAuthData(StepMCChain.MCChain mcChain, ECPublicKey mojangPublicKey) throws InvalidJwtException, JoseException {
        String publicBase64Key = Base64.getEncoder().encodeToString(mcChain.publicKey().getEncoded());

        // adapted from https://github.com/RaphiMC/ViaBedrock/blob/a771149fe4492e4f1393cad66758313067840fcc/src/main/java/net/raphimc/viabedrock/protocol/packets/LoginPackets.java#L276-L291
        JwtConsumer consumer = new JwtConsumerBuilder()
            .setAllowedClockSkewInSeconds(60)
            .setVerificationKey(mojangPublicKey)
            .build();

        JsonWebSignature mojangJws = (JsonWebSignature) consumer.process(mcChain.mojangJwt()).getJoseObjects().get(0);

        JwtClaims claimsSet = new JwtClaims();
        claimsSet.setClaim("certificateAuthority", true);
        claimsSet.setClaim("identityPublicKey", mojangJws.getHeader("x5u"));
        claimsSet.setExpirationTimeMinutesInTheFuture(2 * 24 * 60); // 2 days
        claimsSet.setNotBeforeMinutesInThePast(1);

        JsonWebSignature selfSignedJws = new JsonWebSignature();
        selfSignedJws.setPayload(claimsSet.toJson());
        selfSignedJws.setKey(mcChain.privateKey());
        selfSignedJws.setAlgorithmHeaderValue("ES384");
        selfSignedJws.setHeader(HeaderParameterNames.X509_URL, publicBase64Key);
        
        String selfSignedJwt = selfSignedJws.getCompactSerialization();

        return new ArrayList<>(List.of(selfSignedJwt, mcChain.mojangJwt(), mcChain.identityJwt()));
    }

    public static String forgeOfflineSkinData(KeyPair pair, JSONObject skinData) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue("ES384");
        jws.setHeader(HeaderParameterNames.X509_URL, publicKeyBase64);
        jws.setPayload(skinData.toJSONString());
        jws.setKey(pair.getPrivate());

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static String forgeOnlineSkinData(Account account, JSONObject skinData, String serverAddress) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(account.mcChain().publicKey().getEncoded());

        HashMap<String,Object> overrideData = new HashMap<String,Object>();
        overrideData.put("PlayFabId", account.playFabToken().playFabId());
        overrideData.put("DeviceId", account.mcChain().prevResult().initialXblSession().prevResult2().id());
        overrideData.put("ThirdPartyName", account.mcChain().displayName());
        overrideData.put("ServerAddress", serverAddress);

        skinData.putAll(overrideData);

        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue("ES384");
        jws.setHeader(HeaderParameterNames.X509_URL, publicKeyBase64);
        jws.setPayload(skinData.toJSONString());
        jws.setKey(account.mcChain().privateKey());

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    public static ECPublicKey forgeMojangPublicKey() {
                try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(MOJANG_PUBLIC_KEY)));
        } catch (Throwable e) {
            throw new RuntimeException("Could not initialize the required cryptography for online login", e);
        }
    }
}
