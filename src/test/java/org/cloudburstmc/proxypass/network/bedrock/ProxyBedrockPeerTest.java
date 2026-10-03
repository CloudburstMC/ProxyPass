package org.cloudburstmc.proxypass.network.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.cloudburstmc.netty.channel.raknet.packet.RakMessage;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.v649.Bedrock_v649;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.encryption.BedrockEncryptionEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.nethernet.NetherNetFrameCodec;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;

class ProxyBedrockPeerTest {

    @Test
    void roundTripsSuccessiveBatchesWithTransportAppropriateEncryption() {
        for (Transport transport : Transport.values()) {
            EmbeddedChannel channel = newChannel(transport);
            ProxyBedrockPeer peer = newPeer(channel, transport);
            peer.enableEncryption(new SecretKeySpec(new byte[32], "AES"));

            try {
                for (byte[] payload : new byte[][]{{1, 2, 3, 4}, {5, 6, 7}}) {
                    assertTrue(channel.writeOutbound(BedrockBatchWrapper.newInstance(null, Unpooled.wrappedBuffer(payload))));
                    ByteBuf wire = channel.readOutbound();

                    try {
                        assertEquals(payload.length + 1 + (transport == Transport.RAKNET ? 9 : 0), wire.readableBytes());
                        if (transport == Transport.RAKNET) {
                            assertEquals(0xfe, wire.getUnsignedByte(wire.readerIndex()));
                        }

                        Object inbound = transport == Transport.RAKNET ? new RakMessage(wire.retainedDuplicate()) : wire.retainedDuplicate();
                        assertTrue(channel.writeInbound(inbound));
                        BedrockBatchWrapper decoded = channel.readInbound();

                        try {
                            assertArrayEquals(payload, ByteBufUtil.getBytes(decoded.getUncompressed()));
                        } finally {
                            decoded.release();
                        }
                    } finally {
                        wire.release();
                    }
                }
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }

    @Test
    void enablesEncryptionForOnlyTheNegotiatingConnection() {
        EmbeddedChannel backend = newChannel(Transport.RAKNET);
        EmbeddedChannel client = newChannel(Transport.NETHERNET);
        ProxyBedrockPeer backendPeer = newPeer(backend, Transport.RAKNET);
        newPeer(client, Transport.NETHERNET);

        try {
            backendPeer.enableEncryption(new SecretKeySpec(new byte[32], "AES"));

            assertNotNull(backend.pipeline().get(BedrockEncryptionEncoder.class));
            assertNotNull(backend.pipeline().get(BedrockEncryptionDecoder.class));
            assertNull(client.pipeline().get(BedrockEncryptionEncoder.class));
            assertNull(client.pipeline().get(BedrockEncryptionDecoder.class));
        } finally {
            backend.finishAndReleaseAll();
            client.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsDuplicateEncryptionWithoutReplacingTheActiveCiphers() {
        EmbeddedChannel channel = newChannel(Transport.RAKNET);
        ProxyBedrockPeer peer = newPeer(channel, Transport.RAKNET);

        try {
            peer.enableEncryption(new SecretKeySpec(new byte[32], "AES"));
            BedrockEncryptionEncoder encoder = channel.pipeline().get(BedrockEncryptionEncoder.class);
            BedrockEncryptionDecoder decoder = channel.pipeline().get(BedrockEncryptionDecoder.class);

            assertThrows(IllegalStateException.class, () -> peer.enableEncryption(new SecretKeySpec(new byte[32], "AES")));
            assertSame(encoder, channel.pipeline().get(BedrockEncryptionEncoder.class));
            assertSame(decoder, channel.pipeline().get(BedrockEncryptionDecoder.class));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsNonAesKeysWithoutChangingThePipeline() {
        EmbeddedChannel channel = newChannel(Transport.RAKNET);
        ProxyBedrockPeer peer = newPeer(channel, Transport.RAKNET);

        try {
            assertThrows(IllegalArgumentException.class, () -> peer.enableEncryption(new SecretKeySpec(new byte[32], "HmacSHA256")));
            assertNull(channel.pipeline().get(BedrockEncryptionEncoder.class));
            assertNull(channel.pipeline().get(BedrockEncryptionDecoder.class));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static EmbeddedChannel newChannel(Transport transport) {
        EmbeddedChannel channel = new EmbeddedChannel();
        if (transport == Transport.RAKNET) {
            channel.pipeline().addLast(FrameIdCodec.NAME, new FrameIdCodec(0xfe));
        } else {
            channel.pipeline().addLast(NetherNetFrameCodec.NAME, new NetherNetFrameCodec());
        }

        channel.pipeline()
                .addLast(CompressionCodec.NAME, new CompressionCodec(new SimpleCompressionStrategy(new NoopCompression()), false))
                .addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());
        return channel;
    }

    private static ProxyBedrockPeer newPeer(EmbeddedChannel channel, Transport transport) {
        ProxyBedrockPeer peer = new ProxyBedrockPeer(channel, BedrockClientSession::new, transport);
        peer.setCodec(Bedrock_v649.CODEC);
        peer.setCompression(new SimpleCompressionStrategy(new NoopCompression()));
        return peer;
    }
}
