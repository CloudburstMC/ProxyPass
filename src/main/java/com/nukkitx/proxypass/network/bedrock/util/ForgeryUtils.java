package com.nukkitx.proxypass.network.bedrock.util;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.experimental.UtilityClass;
import net.minidev.json.JSONObject;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
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
    public static final ECPublicKey MOJANG_PUBLIC_KEY = com.nukkitx.protocol.bedrock.util.EncryptionUtils.getMojangPublicKey();

    public static ECPublicKey generateKey(String b64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return com.nukkitx.protocol.bedrock.util.EncryptionUtils.generateKey(b64);
    }

    public static KeyPair createKeyPair() {
        return com.nukkitx.protocol.bedrock.util.EncryptionUtils.createKeyPair();
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
            com.nukkitx.protocol.bedrock.util.EncryptionUtils.signJwt(jwt, privateKey);
        } catch (JOSEException e) {
            throw new RuntimeException("Unable to sign JWT", e);
        }
    }

    public static SecretKey getServerKey(KeyPair proxyKeyPair, PublicKey serverKey, byte[] token) throws InvalidKeyException {
        return com.nukkitx.protocol.bedrock.util.EncryptionUtils.getSecretKey(proxyKeyPair.getPrivate(), serverKey, token);
    }
}
