package org.cloudburstmc.proxypass.gui.model;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PacketInfo {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final int id;
    private final String timestamp;
    private final String direction;
    private final String packetType;
    private final String packetName;
    private final int size;
    private final BedrockPacket packet;
    private String rawData;

    public PacketInfo(int id, String direction, BedrockPacket packet, int size) {
        this.id = id;
        this.timestamp = LocalDateTime.now().format(FORMATTER);
        this.direction = direction;
        this.packet = packet;
        this.packetType = packet.getClass().getSimpleName();
        this.packetName = packet.getPacketType().name();
        this.size = size;
    }

    public int getId() {
        return id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getDirection() {
        return direction;
    }

    public String getPacketType() {
        return packetType;
    }

    public String getPacketName() {
        return packetName;
    }

    public int getSize() {
        return size;
    }

    public String getRawData() {
        return "";
//        return rawData;
    }

    public BedrockPacket getPacket() {
        return packet;
    }
}