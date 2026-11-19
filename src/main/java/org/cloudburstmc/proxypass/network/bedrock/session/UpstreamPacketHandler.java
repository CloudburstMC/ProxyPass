package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.cloudburstmc.netty.util.nethernet.TransportIdentityBinding;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.TokenPayload;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.auth.Account;
import org.cloudburstmc.proxypass.auth.AuthData;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.util.ForgeryUtils;
import org.cloudburstmc.proxypass.network.bedrock.util.ItemDefinitionRegistries;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {

    private final ProxyServerSession session;
    private final ProxyPass proxy;
    private final Account account;
    private JSONObject skinData;
    private ChainValidationResult chain;
    private String clientJwt;
    private ProxyPlayerSession player;
    private AuthPayload authPayload;
    private AuthData authData;
    private static ECPublicKey mojangPublicKey;
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
            ChainValidationResult chain = EncryptionUtils.validatePayload(packet.getAuthPayload());
            this.chain = chain;
            ECPublicKey identityPublicKey;

            if (packet.getAuthPayload() instanceof TokenPayload) {
                identityPublicKey = (ECPublicKey) chain.identityClaims().parsedIdentityPublicKey();
            } else if (packet.getAuthPayload() instanceof CertificateChainPayload) {
                JsonNode payload = ProxyPass.JSON_MAPPER.valueToTree(chain.rawIdentityClaims());
                identityPublicKey = EncryptionUtils.parseKey(payload.get("identityPublicKey").textValue());
            } else {
                throw new IllegalStateException("Unexpected value: " + packet.getAuthPayload());
            }

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

            if (account == null) {
                ChainValidationResult.IdentityData identityData = chain.identityClaims().extraData;
                this.authData = new AuthData(identityData.displayName, UUID.nameUUIDFromBytes(identityData.xuid.getBytes(StandardCharsets.UTF_8)), identityData.xuid);
            } else {
                MinecraftMultiplayerToken token = account.authManager().getMinecraftMultiplayerToken().getCached();
                this.authData = new AuthData(token.getDisplayName(), token.getUuid(), token.getXuid());
            }

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
        log.info("Initializing proxy session for {}", this.session.getSocketAddress());

        KeyPair keyPair = (account != null)
                    ? account.authManager().getSessionKeyPair()
                    : EncryptionUtils.createKeyPair();
        String identityToken = (account != null)
                    ? account.authManager().getMinecraftMultiplayerToken().getCached().getToken()
                    : null;
        this.proxy.newClient(keyPair, this.chain.identityClaims().extraData, identityToken, downstream -> {
            if (!this.session.isConnected()) {
                downstream.getPeer().getChannel().close();
                return;
            }

            downstream.setCodec(ProxyPass.CODEC);

            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);
            this.seedDefinitionRegistries(downstream);

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                this.session,
                downstream,
                this.proxy,
                this.authData,
                this.chain.identityClaims().extraData,
                keyPair
            );
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            LoginPacket login = prepareLoginPacket(proxySession);

            downstream.setPacketHandler(new DownstreamLoginPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);
            this.player.getLogger().logPacket(this.session, packet, true);
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

    private LoginPacket prepareLoginPacket(ProxyPlayerSession proxySession) {
        String jwtSkinData;
        AuthPayload payload;

        if (account == null) {
            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            String forgedAuth = ForgeryUtils.forgeOfflineAuthData(proxySession.getProxyKeyPair(), this.authData);
            jwtSkinData = ForgeryUtils.forgeOfflineSkinData(proxySession.getProxyKeyPair(), this.skinData);
            payload = new CertificateChainPayload(List.of(forgedAuth), AuthType.SELF_SIGNED);

        } else {
            try {
                if (mojangPublicKey == null) {
                    mojangPublicKey = ForgeryUtils.forgeMojangPublicKey();
                }
                if (authPayload == null) {
                    authPayload = ForgeryUtils.forgeOnlineAuthData(account.authManager(), mojangPublicKey);
                }
            } catch (Exception e) {
                log.error("Failed to get login chain", e);
            }

            jwtSkinData = ForgeryUtils.forgeOnlineSkinData(account, this.skinData, this.proxy.getTargetAddress());

            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            payload = authPayload;
        }

        LoginPacket login = new LoginPacket();
        login.setClientJwt(jwtSkinData);
        login.setAuthPayload(payload);
        login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
        return login;
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
