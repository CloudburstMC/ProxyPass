package com.nukkitx.proxypass.network.bedrock.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.shaded.json.JSONObject;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;

import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class ForgeryUtils {

    public static SignedJWT forgeAuthData(KeyPair pair, JSONObject extraData) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        URI x5u = URI.create(publicKeyBase64);

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build();

        long timestamp = System.currentTimeMillis();
        Date nbf = new Date(timestamp - TimeUnit.SECONDS.toMillis(1));
        Date exp = new Date(timestamp + TimeUnit.DAYS.toMillis(1));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .notBeforeTime(nbf)
                .expirationTime(exp)
                .issueTime(exp)
                .issuer("self")
                .claim("certificateAuthority", true)
                .claim("extraData", extraData)
                .claim("identityPublicKey", publicKeyBase64)
                .build();

        SignedJWT jwt = new SignedJWT(header, claimsSet);

        try {
            EncryptionUtils.signJwt(jwt, (ECPrivateKey) pair.getPrivate());
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }

        return jwt;
    }

    public static SignedJWT forgeSkinData(KeyPair pair, JSONObject skinData) {
        URI x5u = URI.create(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build();

        SignedJWT jws;

        try {
            jws = new SignedJWT(header, JWTClaimsSet.parse(skinData));
            EncryptionUtils.signJwt(jws, (ECPrivateKey) pair.getPrivate());
        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }

        return jws;
    }
}
