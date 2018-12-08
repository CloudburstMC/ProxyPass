package com.nukkitx.proxypass.network.bedrock.util;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;
import net.minidev.json.JSONObject;

import javax.crypto.KeyAgreement;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class EncryptionUtils {
    public static final ECPublicKey MOJANG_PUBLIC_KEY;
    private static final String MOJANG_PUBLIC_KEY_BASE64 =
            "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8ELkixyLcwlZryUQcu1TvPOmI2B7vX83ndnWRUaXm74wFfa5f/lwQNTfrLVHa2PmenpGI6JhIMUJaWZrjmMj90NoKNFSNBuKdm8rYiXsfaz3K36x/1U26HpG0ZxK/V1V";
    private static final KeyPairGenerator KEY_PAIR_GEN;

    static {
        try {
            KEY_PAIR_GEN = KeyPairGenerator.getInstance("EC");
            KEY_PAIR_GEN.initialize(new ECGenParameterSpec("secp384r1"));
            MOJANG_PUBLIC_KEY = generateKey(MOJANG_PUBLIC_KEY_BASE64);
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | InvalidKeySpecException e) {
            throw new AssertionError("Unable to initialize required encryption");
        }
    }

    public static ECPublicKey generateKey(String b64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    public static KeyPair createKeyPair() {
        return KEY_PAIR_GEN.generateKeyPair();
    }

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

        signJwt(jwt, (ECPrivateKey) pair.getPrivate());

        return jwt;
    }

    public static JWSObject forgeSkinData(KeyPair pair, JSONObject skinData) {
        URI x5u = URI.create(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build();

        JWSObject jws = new JWSObject(header, new Payload(skinData));

        signJwt(jws, (ECPrivateKey) pair.getPrivate());

        return jws;
    }

    private static void signJwt(JWSObject jwt, ECPrivateKey privateKey) {
        try {
            JWSSigner signer = new ECDSASigner(privateKey, ECKey.Curve.P_384);
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new RuntimeException("Unable to sign JWT", e);
        }
    }

    public static byte[] getServerKey(KeyPair proxyKeyPair, PublicKey serverKey, byte[] token) throws InvalidKeyException {
        byte[] sharedSecret = getSharedSecret(proxyKeyPair.getPrivate(), serverKey);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        digest.update(token);
        digest.update(sharedSecret);
        return digest.digest();
    }

    private static byte[] getSharedSecret(PrivateKey proxyKey, PublicKey serverKey) throws InvalidKeyException {
        KeyAgreement agreement;
        try {
            agreement = KeyAgreement.getInstance("ECDH");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }

        agreement.init(proxyKey);
        agreement.doPhase(serverKey, true);
        return agreement.generateSecret();
    }
}
