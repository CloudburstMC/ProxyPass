package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.TestUtils;

@Getter
@Log4j2
public class ProxyClientSession extends BedrockClientSession implements ProxySession {

    private final ProxyPass proxyPass;
    @Setter
    private BedrockSession sendSession;

    public ProxyClientSession(BedrockPeer peer, int subClientId, ProxyPass proxyPass) {
        super(peer, subClientId);
        this.proxyPass = proxyPass;
    }

    @Override
    protected void onPacket(BedrockPacketWrapper wrapper) {
        BedrockPacket packet = wrapper.getPacket();
        this.logInbound(packet);
        if (proxyPass.getConfiguration().isPacketTesting()) {
            TestUtils.testPacket(this, wrapper);
        }

        if (this.packetHandler == null) {
            log.warn("Received packet without a packet handler for {}:{}: {}", new Object[]{this.getSocketAddress(), this.subClientId, packet});
        } else if (this.packetHandler.handlePacket(packet) == PacketSignal.UNHANDLED && this.sendSession != null) {
            this.sendSession.sendPacket(packet);
        }
    }
}
