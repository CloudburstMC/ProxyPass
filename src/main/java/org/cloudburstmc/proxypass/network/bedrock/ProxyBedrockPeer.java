package org.cloudburstmc.proxypass.network.bedrock;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.*;
import org.cloudburstmc.protocol.common.util.Zlib;
import org.cloudburstmc.proxypass.network.Transport;

import javax.crypto.SecretKey;
import java.util.Objects;

public class ProxyBedrockPeer extends BedrockPeer {

    private final Transport transport;

    public ProxyBedrockPeer(Channel channel, BedrockSessionFactory factory, Transport transport) {
        super(channel, factory);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public void setCompression(PacketCompressionAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm");
        if (this.transport == Transport.RAKNET) {
            super.setCompression(algorithm);
            return;
        }

        CompressionStrategy strategy = switch (algorithm) {
            case ZLIB -> new SimpleCompressionStrategy(new ZlibCompression(Zlib.RAW));
            case SNAPPY -> new SimpleCompressionStrategy(new SnappyCompression());
            case NONE -> new SimpleCompressionStrategy(new NoopCompression());
        };

        super.setCompression(strategy);
    }

    @Override
    public void enableEncryption(@NonNull SecretKey key) {
        Objects.requireNonNull(key, "key");
        if (this.transport.supportsPacketEncryption()) {
            super.enableEncryption(key);
        }
    }
}
