package com.nukkitx.proxypass.network.bedrock.session;

import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.ClientToServerHandshakePacket;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.packet.NetworkSettingsPacket;
import com.nukkitx.protocol.bedrock.packet.ServerToClientHandshakePacket;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import com.nukkitx.proxypass.ProxyPass;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.crypto.SecretKey;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.Base64;

@Log4j2
@RequiredArgsConstructor
public class DownstreamInitialPacketHandler implements BedrockPacketHandler {
    private final BedrockClientSession session;
    private final ProxyPlayerSession player;
    private final ProxyPass proxy;
    private final LoginPacket loginPacket;

    @Override
    public boolean handle(NetworkSettingsPacket packet) {
        this.session.setCompression(packet.getCompressionAlgorithm());
        log.info("Compression algorithm picked {}", packet.getCompressionAlgorithm());

        this.session.sendPacketImmediately(this.loginPacket);
        return true;
    }

    public boolean handle(ServerToClientHandshakePacket packet) {
        try {
            SignedJWT saltJwt = SignedJWT.parse(packet.getJwt());
            URI x5u = saltJwt.getHeader().getX509CertURL();
            ECPublicKey serverKey = EncryptionUtils.generateKey(x5u.toASCIIString());
            SecretKey key = EncryptionUtils.getSecretKey(this.player.getProxyKeyPair().getPrivate(), serverKey,
                    Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt")));
            session.enableEncryption(key);
        } catch (ParseException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
        session.sendPacketImmediately(clientToServerHandshake);


        this.session.setPacketHandler(new DownstreamPacketHandler(this.session, this.player, this.proxy));
        log.debug("Downstream connected");
        return true;
    }
}
