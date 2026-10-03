package org.cloudburstmc.proxypass.network.bedrock;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.nethernet.NetherNetFrameCodec;

import java.util.function.BiFunction;
import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class ProxyChannelInitializer<T extends BedrockSession> extends BedrockChannelInitializer<T> {

    private static final NetherNetFrameCodec FRAME_CODEC = new NetherNetFrameCodec();

    private final ProxyPass proxy;
    private final Transport transport;
    private final boolean incoming;
    private final BiFunction<BedrockPeer, Integer, T> factory;
    private final Consumer<T> initializer;

    @Override
    protected void preInitChannel(Channel channel) throws Exception {
        if (!this.proxy.registerConnection(channel, this.incoming)) {
            channel.close();
            throw new IllegalStateException("Proxy connection limit reached");
        }

        channel.attr(PacketDirection.ATTRIBUTE).set(this.incoming ? PacketDirection.CLIENT_BOUND : PacketDirection.SERVER_BOUND);

        if (this.transport == Transport.RAKNET) {
            super.preInitChannel(channel);
        } else {
            channel.pipeline()
                    .addLast(NetherNetFrameCodec.NAME, FRAME_CODEC)
                    .addLast(CompressionCodec.NAME, new CompressionCodec(new SimpleCompressionStrategy(new NoopCompression()), false));
        }
    }

    @Override
    protected void initPacketCodec(Channel channel) throws Exception {
        if (this.transport == Transport.RAKNET) {
            super.initPacketCodec(channel);
        } else {
            channel.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
        }
    }

    @Override
    protected BedrockPeer createPeer(Channel channel) {
        return new ProxyBedrockPeer(channel, this::createSession, this.transport);
    }

    @Override
    protected T createSession0(BedrockPeer peer, int subClientId) {
        return this.factory.apply(peer, subClientId);
    }

    @Override
    protected void initSession(T session) {
        this.initializer.accept(session);
    }

    @Override
    protected void postInitChannel(Channel channel) {
        channel.pipeline().addLast("proxy-exception-handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
                log.warn("Closing connection {} after a protocol error", context.channel().remoteAddress(), cause);
                context.close();
            }
        });
    }
}
