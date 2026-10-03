package org.cloudburstmc.proxypass.network.bedrock.session;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.TestUtils;
import org.cloudburstmc.proxypass.ui.UIPacketData;

@Getter
@Log4j2
public class ProxyServerSession extends BedrockServerSession implements ProxySession {

    private final ProxyPass proxyPass;
    @Setter
    private volatile BedrockSession sendSession;
    @Setter
    private volatile ProxyPlayerSession player;

    public ProxyServerSession(BedrockPeer peer, int subClientId, ProxyPass proxyPass) {
        super(peer, subClientId);
        this.proxyPass = proxyPass;
        peer.getChannel().closeFuture().addListener(ignored -> {
            ProxyPlayerSession connectedPlayer = this.player;
            if (connectedPlayer != null) {
                connectedPlayer.close();
            }
        });
    }

    @Override
    protected void onPacket(BedrockPacketWrapper wrapper) {
        BedrockPacket packet = wrapper.getPacket();

        if (proxyPass.isBlockedPacket(packet.getClass())) {
            return;
        }

        if (player != null) {
            player.logger.logPacket(this, packet, true);
            player.getExtraLogHandler().accept(wrapper, UIPacketData.Direction.C2S);
        }

        if (proxyPass.getConfiguration().isPacketTesting()) {
            TestUtils.testPacket(this, wrapper);
        }

        if (this.packetHandler == null) {
            log.warn("Received packet without a packet handler for {}:{}: {}", new Object[]{this.getSocketAddress(), this.subClientId, packet});
        } else if (this.packetHandler.handlePacket(packet) == PacketSignal.UNHANDLED && this.sendSession != null) {
            ByteBuf buffer = wrapper.getPacketBuffer()
                    .retainedSlice()
                    .skipBytes(wrapper.getHeaderLength());

            UnknownPacket sendPacket = new UnknownPacket();
            sendPacket.setPayload(buffer);
            sendPacket.setPacketId(wrapper.getPacketId());
            this.sendSession.sendPacket(sendPacket);
        }
    }

}
