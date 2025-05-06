package org.cloudburstmc.proxypass.network.bedrock.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.PacketSerializeException;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.proxypass.ProxyPass;

import java.util.Objects;
import java.util.Set;

@Log4j2
@UtilityClass
public class TestUtils {

    private static final Set<Class<?>> IGNORE_BUFFER_TEST = Set.of(
            SetEntityDataPacket.class,  // Entity data flags are never serialised the same as vanilla and there is no way
            AddEntityPacket.class,      // around it without removing the nice features we have.
            AddItemEntityPacket.class,
            AddPlayerPacket.class,
            AvailableCommandsPacket.class, // We do too much processing on this packet to make it serialise the same.
            ClientCacheMissResponsePacket.class,
            LoginPacket.class,
            CompressedBiomeDefinitionListPacket.class
    );

    public static void testPacket(BedrockSession session, BedrockPacketWrapper wrapper) {
        BedrockPacket packet = wrapper.getPacket();
        if (!(packet instanceof UnknownPacket)) {
            int packetId = ProxyPass.CODEC.getPacketDefinition(packet.getClass()).getId();
            ByteBuf buffer = ByteBufAllocator.DEFAULT.ioBuffer();
            ByteBuf originalBuffer = wrapper.getPacketBuffer();
            // Get packet buffer without header.
            originalBuffer = originalBuffer.slice(originalBuffer.readerIndex() + wrapper.getHeaderLength(),
                    originalBuffer.readableBytes() - wrapper.getHeaderLength());
            try {
                BedrockCodecHelper helper = session.getPeer().getCodecHelper();
                ProxyPass.CODEC.tryEncode(helper, buffer, packet);
                boolean packetFailed = false;
                if (!IGNORE_BUFFER_TEST.contains(packet.getClass()) && !originalBuffer.equals(buffer)) {
                    // Something went wrong in serialization.
                    log.warn("Packet's buffers not equal for {}:\n Original  : {}\nRe-encoded : {}",
                            packet.getClass().getSimpleName(), ByteBufUtil.hexDump(originalBuffer), ByteBufUtil.hexDump(buffer));
                }

                BedrockPacket packet2 = ProxyPass.CODEC.tryDecode(helper, buffer, packetId);
                if (!Objects.equals(packet, packet2)) {
                    // Something went wrong in serialization.
                    log.warn("Packet's instances not equal:\n Original  : {}\nRe-encoded : {}",
                            packet, packet2);
                } else if (packetFailed) {
                    log.info("Packet instances equal but buffers not equal for {}:", packet.getClass().getSimpleName());
                }
            } catch (PacketSerializeException e) {
                //ignore
            } finally {
                buffer.release();
            }
        }
    }
}
