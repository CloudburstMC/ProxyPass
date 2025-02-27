package org.cloudburstmc.proxypass.gui;

import io.netty.buffer.ByteBuf;
import javafx.collections.transformation.FilteredList;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PacketFormatter {

    protected static void exportAsText(File file, FilteredList<PacketInfo> filteredPackets) throws Exception {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("ProxyPass Packet Export");
            writer.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.println("Total Packets: " + filteredPackets.size());
            writer.println("========================================================");

            for (PacketInfo packet : filteredPackets) {
                writer.println("Packet ID: " + packet.getId());
                writer.println("Timestamp: " + packet.getTimestamp());
                writer.println("Direction: " + packet.getDirection());
                writer.println("Packet Type: " + packet.getPacketType());
                writer.println("Packet Name: " + packet.getPacketName());
                writer.println("Size: " + packet.getSize() + " bytes");
                writer.println("\nContent:");
                writer.println(packet.getPacket().toString());
                writer.println("========================================================");
            }
        }
    }

    protected static void exportAsJson(File file, FilteredList<PacketInfo> filteredPackets) throws Exception {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("{");
            writer.println("  \"meta\": {");
            writer.println("    \"generated\": \"" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\",");
            writer.println("    \"totalPackets\": " + filteredPackets.size());
            writer.println("  },");
            writer.println("  \"packets\": [");

            boolean first = true;
            for (PacketInfo packet : filteredPackets) {
                if (!first) {
                    writer.println("    },");
                }
                first = false;

                writer.println("    {");
                writer.println("      \"id\": " + packet.getId() + ",");
                writer.println("      \"timestamp\": \"" + packet.getTimestamp() + "\",");
                writer.println("      \"direction\": \"" + packet.getDirection() + "\",");
                writer.println("      \"packetType\": \"" + packet.getPacketType() + "\",");
                writer.println("      \"packetName\": \"" + packet.getPacketName() + "\",");
                writer.println("      \"size\": " + packet.getSize() + ",");

                // Format the packet content as a JSON string
                String packetContent = packet.getPacket().toString()
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n");

                writer.println("      \"content\": \"" + packetContent + "\",");
            }

            if (!filteredPackets.isEmpty()) {
                writer.println("    }");
            }

            writer.println("  ]");
            writer.println("}");
        }
    }

    public static String shortHex(ByteBuf buf) {
        int size = buf.readableBytes();
        StringBuilder hexData = new StringBuilder();
        for (int i = 0; i < Math.min(size, 100); i++) {
            hexData.append(String.format("%02X ", buf.getByte(i) & 0xFF));
            if ((i + 1) % 16 == 0) {
                hexData.append("\n");
            }
        }
        if (size > 100) {
            hexData.append("...");
        }
        return hexData.toString();
    }
}