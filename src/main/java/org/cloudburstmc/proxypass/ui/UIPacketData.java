package org.cloudburstmc.proxypass.ui;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.time.Instant;

public record UIPacketData(
        long internalIndex,
        BedrockPacket packet,
        int packetId,
        Instant timeCaptured,
        Direction direction,
        byte[] rawBytes,
        String jsonString
) {
    public enum Direction {
        S2C, C2S
    }

    public static long freeIndex = Long.MIN_VALUE;
}
