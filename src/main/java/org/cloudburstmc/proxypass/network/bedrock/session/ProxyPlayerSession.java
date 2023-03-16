package org.cloudburstmc.proxypass.network.bedrock.session;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.PacketSerializeException;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.logging.SessionLogger;

import java.security.KeyPair;
import java.util.Objects;

@Log4j2
@Getter
public class ProxyPlayerSession {
    private final BedrockSession upstream;
    private final BedrockSession downstream;
    private final ProxyPass proxy;
    private final AuthData authData;
    private final long timestamp = System.currentTimeMillis();
    @Getter(AccessLevel.PACKAGE)
    private final KeyPair proxyKeyPair = EncryptionUtils.createKeyPair();
    private volatile boolean closed = false;

    public final SessionLogger logger;

    public ProxyPlayerSession(BedrockSession upstream, BedrockSession downstream, ProxyPass proxy, AuthData authData) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.proxy = proxy;
        this.authData = authData;
//        this.upstream.addDisconnectHandler(reason -> {
//            if (reason != DisconnectReason.DISCONNECTED) {
//                this.downstream.disconnect();
//            }
//        });
        this.logger = new SessionLogger(
                proxy,
                proxy.getSessionsDir(),
                this.authData.getDisplayName(),
                timestamp
        );
        logger.start();
    }

    public Handler getUpstreamBatchHandler() {
        return new Handler(downstream, logger, true);
    }

    public Handler getDownstreamTailHandler() {
        return new Handler(upstream, logger, false);
    }

    public class Handler implements BedrockPacketHandler {
        private final SessionLogger logger;
        private final BedrockSession session;
        private final boolean upstream;
        @Getter
        @Setter
        private BedrockPacketHandler handler;

        private Handler(BedrockSession fromSession, SessionLogger logger, boolean upstream) {
            this.session = fromSession;
            this.logger = logger;
            this.upstream = upstream;
        }

        @Override
        public PacketSignal handlePacket(BedrockPacket packet) {
            PacketSignal signal = packet.handle(handler);

            if (signal == PacketSignal.UNHANDLED || signal == null) {
                this.session.sendPacket(packet);
            }

            boolean packetTesting = ProxyPlayerSession.this.proxy.getConfiguration().isPacketTesting();

            if (packetTesting && !(packet instanceof UnknownPacket)) {
                int packetId = ProxyPass.CODEC.getPacketDefinition(packet.getClass()).getId();
                ByteBuf buffer = ByteBufAllocator.DEFAULT.ioBuffer();
                try {
                    BedrockCodecHelper helper = session.getPeer().getCodecHelper();
                    ProxyPass.CODEC.tryEncode(helper, buffer, packet);
                    BedrockPacket packet2 = ProxyPass.CODEC.tryDecode(helper, buffer, packetId);
                    if (!Objects.equals(packet, packet2)) {
                        // Something went wrong in serialization.
                        log.warn("Packets instances not equal:\n Original  : {}\nRe-encoded : {}",
                                packet, packet2);
                    }
                } catch (PacketSerializeException e) {
                    //ignore
                } finally {
                    buffer.release();
                }
            }
            return PacketSignal.HANDLED;
        }

        @Override
        public void onDisconnect(String reason) {
            this.session.disconnect(reason);
        }
    }
}
