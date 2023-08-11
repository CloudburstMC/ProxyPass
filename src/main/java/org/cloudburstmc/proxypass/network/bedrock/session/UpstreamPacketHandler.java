package org.cloudburstmc.proxypass.network.bedrock.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.ForgeryUtils;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {
    private final ProxyServerSession session;
    private final ProxyPass proxy;
    private final Account account;
    private JSONObject skinData;
    private JSONObject extraData;
    private List<String> chainData;
    private AuthData authData;
    private ProxyPlayerSession player;

    private static ECPublicKey mojangPublicKey;
    private static List<String> onlineLoginChain;

    private static boolean verifyJwt(String jwt, PublicKey key) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(key);
        jws.setCompactSerialization(jwt);

        return jws.verifySignature();
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
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

        NetworkSettingsPacket networkSettingsPacket = new NetworkSettingsPacket();
        networkSettingsPacket.setCompressionThreshold(0);
        networkSettingsPacket.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);

        session.sendPacketImmediately(networkSettingsPacket);
        session.setCompression(PacketCompressionAlgorithm.ZLIB);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        try {
            ChainValidationResult chain = EncryptionUtils.validateChain(packet.getChain());

            JsonNode payload = ProxyPass.JSON_MAPPER.valueToTree(chain.rawIdentityClaims());

            if (payload.get("extraData").getNodeType() != JsonNodeType.OBJECT) {
                throw new RuntimeException("AuthData was not found!");
            }

            extraData = new JSONObject(JsonUtils.childAsType(chain.rawIdentityClaims(), "extraData", Map.class));

            if (payload.get("identityPublicKey").getNodeType() != JsonNodeType.STRING) {
                throw new RuntimeException("Identity Public Key was not found!");
            }
            ECPublicKey identityPublicKey = EncryptionUtils.parseKey(payload.get("identityPublicKey").textValue());

            String clientJwt = packet.getExtra();
            verifyJwt(clientJwt, identityPublicKey);
            JsonWebSignature jws = new JsonWebSignature();
            jws.setCompactSerialization(clientJwt);

            skinData = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));

            if (account == null) {
                this.authData = new AuthData(chain.identityClaims().extraData.displayName,
                    chain.identityClaims().extraData.identity, chain.identityClaims().extraData.xuid);
                chainData = packet.getChain();

                initializeOfflineProxySession();
            } else {
                this.authData = new AuthData(account.mcChain().displayName(), account.mcChain().id(), account.mcChain().xuid());

                initializeOnlineProxySession();
            }
        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", e);
        }
        return PacketSignal.HANDLED;
    }

    private void initializeOfflineProxySession() {
        log.debug("Initializing proxy session");
        this.proxy.newClient(this.proxy.getTargetAddress(), downstream -> {
            downstream.setCodec(ProxyPass.CODEC);
            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                this.session, 
                downstream, 
                this.proxy, 
                this.authData, 
                EncryptionUtils.createKeyPair());
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            try {
                String jwt = chainData.get(chainData.size() - 1);
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(jwt);
                player.getLogger().saveJson("chainData", new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload())));
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }
            String authData = ForgeryUtils.forgeOfflineAuthData(proxySession.getProxyKeyPair(), extraData);
            String skinData = ForgeryUtils.forgeOfflineSkinData(proxySession.getProxyKeyPair(), this.skinData);
            chainData.remove(chainData.size() - 1);
            chainData.add(authData);

            LoginPacket login = new LoginPacket();
            login.getChain().addAll(chainData);
            login.setExtra(skinData);
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.setPacketHandler(new DownstreamInitialPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);

            //SkinUtils.saveSkin(proxySession, this.skinData);
        });
    }

    private void initializeOnlineProxySession() {
        log.debug("Initializing proxy session");
        this.proxy.newClient(this.proxy.getTargetAddress(), downstream -> {
            try {
                if (mojangPublicKey == null) {
                    mojangPublicKey = ForgeryUtils.forgeMojangPublicKey();
                }
                if (onlineLoginChain == null) {
                    onlineLoginChain = ForgeryUtils.forgeOnlineAuthData(account.mcChain(), mojangPublicKey);
                }
            } catch (Exception e) {
                log.error("Failed to get login chain", e);
            }

            downstream.setCodec(ProxyPass.CODEC);
            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                this.session, 
                downstream, 
                this.proxy, 
                this.authData, 
                new KeyPair(account.mcChain().publicKey(), account.mcChain().privateKey()));
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            String skinData = ForgeryUtils.forgeOnlineSkinData(account, this.skinData, this.proxy.getTargetAddress().getHostString());

            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            LoginPacket login = new LoginPacket();
            login.getChain().addAll(onlineLoginChain);
            login.setExtra(skinData);
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.setPacketHandler(new DownstreamInitialPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);

            //SkinUtils.saveSkin(proxySession, this.skinData);
        });
    }
}
