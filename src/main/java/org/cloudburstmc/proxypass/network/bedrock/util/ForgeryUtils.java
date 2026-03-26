package org.cloudburstmc.proxypass.network.bedrock.util;

import lombok.experimental.UtilityClass;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult.IdentityData;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import java.security.KeyPair;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@UtilityClass
public class ForgeryUtils {

    public static String forgeToken(KeyPair pair, IdentityData data) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        long timestamp = System.currentTimeMillis();
        Date nbf = new Date(timestamp - TimeUnit.SECONDS.toMillis(1));
        Date exp = new Date(timestamp + TimeUnit.DAYS.toMillis(1));

        JwtClaims claims = new JwtClaims();
        claims.setNotBefore(NumericDate.fromMilliseconds(nbf.getTime()));
        claims.setExpirationTime(NumericDate.fromMilliseconds(exp.getTime()));
        claims.setIssuedAt(NumericDate.fromMilliseconds(timestamp));
        claims.setClaim("cpk", publicKeyBase64);
        claims.setClaim("xname", data.displayName);
        claims.setClaim("xid", data.xuid);
        if (data.minecraftId != null) {
            claims.setClaim("mid", data.minecraftId);
        }

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(pair.getPrivate());
        jws.setAlgorithmHeaderValue("ES384");
        jws.setHeader(HeaderParameterNames.X509_URL, publicKeyBase64);

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    public static String forgeSkinData(KeyPair pair, JSONObject skinData) {
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
}
