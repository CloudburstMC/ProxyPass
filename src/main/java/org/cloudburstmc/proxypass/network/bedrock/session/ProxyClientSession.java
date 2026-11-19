package org.cloudburstmc.proxypass.network.bedrock.session;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.DisconnectPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.TestUtils;
import org.cloudburstmc.proxypass.ui.UIPacketData;

@Getter
@Log4j2
public class ProxyClientSession extends BedrockClientSession implements ProxySession {

    private final ProxyPass proxyPass;

    @Setter
    private volatile BedrockSession sendSession;
    @Setter
    private volatile ProxyPlayerSession player;

    public ProxyClientSession(BedrockPeer peer, int subClientId, ProxyPass proxyPass) {
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
        
        if (packet instanceof DisconnectPacket disconnectPacket) {
            if (this.player != null) {
                this.player.getUpstream().sendPacketImmediately(disconnectPacket);
                this.player.getOnClose().run();
                this.player.close();
            }
            String message = "Player %s (%s) was disconnected with reason %s and kick message %s".formatted(this.player.getIdentityData().displayName, this.player.getIdentityData().xuid, disconnectPacket.getReason().name(), disconnectPacket.getKickMessage());
            this.disconnect(message);
            log.info(message);
            this.player.logger.logPacket(this, packet, false);
            return;
        }

        if (proxyPass.isBlockedPacket(packet.getClass())) return; // Just don't send it

        if (this.player != null) {
            this.player.logger.logPacket(this, packet, false);
        }
        player.getExtraLogHandler().accept(wrapper, UIPacketData.Direction.S2C);

        if (proxyPass.getConfiguration().isPacketTesting()) {
            TestUtils.testPacket(this, wrapper);
        }

        if (this.packetHandler == null) {
            log.warn("Received packet without a packet handler for {}:{}: {}", new Object[]{this.getSocketAddress(), this.subClientId, packet});
        } else if (this.packetHandler.handlePacket(packet) == PacketSignal.UNHANDLED && this.sendSession != null) {
            // this.sendSession.sendPacket(ReferenceCountUtil.retain(packet));

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
