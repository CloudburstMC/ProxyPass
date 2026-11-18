package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.jose4j.json.JsonUtil;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

@Log4j2
@RequiredArgsConstructor
public class DownstreamLoginPacketHandler implements BedrockPacketHandler {

    private final ProxyClientSession session;
    private final ProxyPlayerSession player;
    private final ProxyPass proxy;
    private final LoginPacket loginPacket;

    private DownstreamPacketHandler packetHandler;
    private boolean handshakeReceived;

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        PacketSignal signal = BedrockPacketHandler.super.handlePacket(packet);
        if (signal == PacketSignal.UNHANDLED) {
            if (this.packetHandler == null) {
                throw new IllegalStateException("Backend sent a packet before network settings");
            }

            return this.packetHandler.handlePacket(packet);
        }

        return signal;
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket packet) {
        if (this.packetHandler != null) {
            throw new IllegalStateException("Backend sent duplicate network settings");
        }

        this.session.setCompression(packet.getCompressionAlgorithm());
        this.packetHandler = new DownstreamPacketHandler(this.session, this.player, this.proxy);
        log.info("Backend selected {} compression for {}", packet.getCompressionAlgorithm(), this.session.getSocketAddress());
        this.session.sendPacketImmediately(this.loginPacket);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket packet) {
        if (this.packetHandler == null || this.handshakeReceived) {
            throw new IllegalStateException("Backend sent an unexpected encryption handshake");
        }

        try {
            JsonWebSignature signature = new JsonWebSignature();
            signature.setCompactSerialization(packet.getJwt());
            ECPublicKey serverKey = EncryptionUtils.parseKey(signature.getHeader(HeaderParameterNames.X509_URL));
            signature.setKey(serverKey);
            if (!signature.verifySignature()) {
                throw new JoseException("Backend handshake signature is invalid");
            }

            byte[] salt = Base64.getDecoder().decode(JsonUtils.childAsType(JsonUtil.parseJson(signature.getPayload()), "salt", String.class));
            if (this.proxy.getConfiguration().getTransport().supportsPacketEncryption()) {
                SecretKey key = EncryptionUtils.getSecretKey(this.player.getProxyKeyPair().getPrivate(), serverKey, salt);
                this.session.enableEncryption(key);
            }

            this.handshakeReceived = true;
            this.session.sendPacketImmediately(new ClientToServerHandshakePacket());
            log.debug("Backend login handshake acknowledged for {}", this.session.getSocketAddress());
        } catch (JoseException | GeneralSecurityException failure) {
            throw new IllegalStateException("Unable to acknowledge backend login handshake", failure);
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        if (this.session.getSendSession() != null && this.session.getSendSession().isConnected()) {
            this.session.getSendSession().disconnect(reason);
        }
    }
}
