package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult.IdentityClaims;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.util.ForgeryUtils;
import org.cloudburstmc.proxypass.network.bedrock.util.ItemDefinitionRegistries;
import org.cloudburstmc.proxypass.network.bedrock.util.SkinUtils;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {

    private final ProxyServerSession session;
    private final ProxyPass proxy;
    private JSONObject skinData;
    private ChainValidationResult chain;
    private String clientJwt;
    private ProxyPlayerSession player;
    private boolean networkConfigured;
    private boolean loginStarted;
    private boolean awaitingHandshake;

    private static boolean verifyJwt(String jwt, PublicKey key) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(key);
        jws.setCompactSerialization(jwt);

        return jws.verifySignature();
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        if (this.networkConfigured || this.loginStarted) {
            this.session.disconnect("Unexpected network settings request");
            return PacketSignal.HANDLED;
        }

        int protocolVersion = packet.getProtocolVersion();
        if (protocolVersion != ProxyPass.PROTOCOL_VERSION) {
            PlayStatusPacket status = new PlayStatusPacket();
            if (protocolVersion > ProxyPass.PROTOCOL_VERSION) {
                status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD);
            } else {
                status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
            }

            session.sendPacketImmediately(status);
            return PacketSignal.HANDLED;
        }

        session.setCodec(ProxyPass.CODEC);
        this.networkConfigured = true;

        NetworkSettingsPacket networkSettingsPacket = new NetworkSettingsPacket();
        networkSettingsPacket.setCompressionThreshold(0);
        networkSettingsPacket.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);

        session.sendPacketImmediately(networkSettingsPacket);
        session.setCompression(PacketCompressionAlgorithm.ZLIB);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        if (!this.networkConfigured || this.loginStarted) {
            this.session.disconnect("Unexpected login packet");
            return PacketSignal.HANDLED;
        }

        this.loginStarted = true;

        try {
            chain = EncryptionUtils.validatePayload(packet.getAuthPayload());
            clientJwt = packet.getClientJwt();

            IdentityClaims claims = chain.identityClaims();
            ECPublicKey identityPublicKey = (ECPublicKey) claims.parsedIdentityPublicKey();
            String clientJwt = packet.getClientJwt();

            if (!verifyJwt(clientJwt, identityPublicKey)) {
                throw new JoseException("Client data signature is invalid");
            }

            String mismatch = TransportIdentityBinding.mismatch(this.session.getPeer().getChannel(), identityPublicKey);
            if (mismatch != null) {
                throw new JoseException("Login identity does not match the transport identity");
            }

            if (this.proxy.getConfiguration().getTransport() == Transport.NETHERNET && this.proxy.getConfiguration().getNethernet().isVerifyClientAuthentication() && !chain.signed()) {
                throw new JoseException("An authenticated client login is required");
            }

            JsonWebSignature jws = new JsonWebSignature();
            jws.setCompactSerialization(clientJwt);

            skinData = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));
            if (!this.proxy.getConfiguration().getTransport().supportsPacketEncryption()) {
                this.initializeProxySession();
            } else {
                KeyPair handshakeKey = EncryptionUtils.createKeyPair();
                byte[] salt = EncryptionUtils.generateRandomToken();
                SecretKey secret = EncryptionUtils.getSecretKey(handshakeKey.getPrivate(), identityPublicKey, salt);
                ServerToClientHandshakePacket handshake = new ServerToClientHandshakePacket();
                handshake.setJwt(EncryptionUtils.createHandshakeJwt(handshakeKey, salt));
                this.awaitingHandshake = true;
                this.session.sendPacketImmediately(handshake);
                this.session.enableEncryption(secret);
            }
        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", e);
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ClientToServerHandshakePacket packet) {
        if (!this.awaitingHandshake) {
            this.session.disconnect("Unexpected encryption handshake");
            return PacketSignal.HANDLED;
        }

        this.awaitingHandshake = false;
        this.initializeProxySession();
        return PacketSignal.HANDLED;
    }

    private void initializeProxySession() {
        log.debug("Initializing proxy session");
        KeyPair keyPair = EncryptionUtils.createKeyPair();
        this.proxy.newClient(keyPair, this.chain.identityClaims().extraData, downstream -> {
            if (!this.session.isConnected()) {
                downstream.getPeer().getChannel().close();
                return;
            }

            downstream.setCodec(ProxyPass.CODEC);
            downstream.setSendSession(this.session);
            downstream.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.CLIENT);
            this.session.setSendSession(downstream);
            this.seedDefinitionRegistries(downstream);

            ProxyPlayerSession proxySession = new ProxyPlayerSession(this.session, downstream, this.proxy, this.chain.identityClaims().extraData, keyPair);
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            try {
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(clientJwt);
                player.getLogger().saveJson("chainData", new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload())));
                player.getLogger().saveJson("skinData", this.skinData);
                SkinUtils.saveSkin(proxySession, this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: {}", e.getMessage(), e);
            }

            String authToken = ForgeryUtils.forgeToken(proxySession.getProxyKeyPair(), this.chain.identityClaims().extraData);
            String skinData = ForgeryUtils.forgeSkinData(proxySession.getProxyKeyPair(), this.skinData);

            LoginPacket login = new LoginPacket();
            login.setAuthPayload(new TokenPayload(authToken, AuthType.SELF_SIGNED));
            login.setClientJwt(skinData);
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.setPacketHandler(new DownstreamLoginPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);

        }).whenComplete((downstream, failure) -> {
            if (failure != null) {
                log.warn("Unable to connect to backend {}", this.proxy.getTargetAddress(), failure);
                this.session.getPeer().getChannel().eventLoop().execute(() -> {
                    if (this.session.isConnected()) {
                        this.session.disconnect("Unable to connect to the backend");
                    }
                });
            }
        });
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        if (this.session.getSendSession() != null && this.session.getSendSession().isConnected()) {
            this.session.getSendSession().disconnect(reason);
        }
    }

    private void seedDefinitionRegistries(ProxyClientSession downstream) {
        DefinitionRegistry<ItemDefinition> itemDefinitions = ItemDefinitionRegistries.empty();
        downstream.getPeer().getCodecHelper().setBlockDefinitions(this.proxy.getBlockDefinitions());
        downstream.getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);
        this.session.getPeer().getCodecHelper().setBlockDefinitions(this.proxy.getBlockDefinitions());
        this.session.getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);
    }
}
