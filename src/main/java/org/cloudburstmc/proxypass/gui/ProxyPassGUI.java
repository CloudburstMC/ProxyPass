package org.cloudburstmc.proxypass.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import lombok.Getter;
import org.cloudburstmc.proxypass.gui.component.FilterComponent;
import org.cloudburstmc.proxypass.gui.component.PacketTableComponent;
import org.cloudburstmc.proxypass.gui.component.StatusBarComponent;
import org.cloudburstmc.proxypass.gui.model.PacketInfo;
import org.cloudburstmc.proxypass.gui.util.FilterManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProxyPassGUI extends Application {
    @Getter
    private static boolean enable = false;
    private static List<PacketInfo> capturedPackets = new CopyOnWriteArrayList<>();
    private static int packetCounter = 0;
    private static ProxyPassGUI instance;

    // UI组件
    private PacketTableComponent tableComponent;
    private TextArea packetDetailsArea;
    private FilterComponent filterComponent;
    private StatusBarComponent statusBarComponent;

    // 配置
    public static Set<String> ignoredPacketTypes = new HashSet<>();

    public static void main(String[] args) {
        launch(args);
    }

    public static void addPacket(String direction, org.cloudburstmc.protocol.bedrock.packet.BedrockPacket packet, int size) {
        // 获取包类型名称
        String packetType = packet.getClass().getSimpleName();

        // 如果此类型的包在忽略列表中，则直接返回
        if (ignoredPacketTypes.contains(packetType)) {
            return;
        }

        PacketInfo packetInfo = new PacketInfo(++packetCounter, direction, packet, size);
        capturedPackets.add(packetInfo);

        Platform.runLater(() -> {
            if (instance != null) {
                instance.tableComponent.addPacket(packetInfo);
                instance.statusBarComponent.updateStatusThrottled();
                instance.filterComponent.scrollToBottomIfEnabled();
                FilterManager.getInstance().checkForNewPacketTypes(packetInfo.getPacketType());
            }
        });
    }

    @Override
    public void start(Stage primaryStage) {
        ProxyPassGUI.enable = true;
        instance = this;
        primaryStage.setTitle("ProxyPass - Minecraft Bedrock Protocol Analyzer");

        BorderPane root = new BorderPane();

        // 创建表格组件
        tableComponent = new PacketTableComponent(capturedPackets, this::displayPacketDetails);
        root.setCenter(tableComponent.getNode());

        // 创建过滤器组件
        filterComponent = new FilterComponent(tableComponent, this::clearAllPackets);
        root.setTop(filterComponent.getNode());

        // 创建数据包详情区域
        packetDetailsArea = new TextArea();
        packetDetailsArea.setEditable(false);
        packetDetailsArea.setWrapText(true);
        packetDetailsArea.setPrefHeight(200);
        root.setBottom(packetDetailsArea);

        // 创建状态栏组件
        statusBarComponent = new StatusBarComponent(tableComponent);
        filterComponent.addNode(statusBarComponent.getNode());

        // 设置场景
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.show();

        statusBarComponent.updateStatus();
    }

    private void clearAllPackets() {
        capturedPackets.clear();
        packetCounter = 0;
        tableComponent.clearPackets();
        packetDetailsArea.clear();
        FilterManager.getInstance().clearKnownPacketTypes();
        statusBarComponent.updateStatus();
    }

    private void displayPacketDetails(PacketInfo packet) {
        if (packet == null) {
            packetDetailsArea.clear();
            return;
        }

        StringBuilder details = new StringBuilder();
        details.append("Packet ID: ").append(packet.getId()).append("\n");
        details.append("Timestamp: ").append(packet.getTimestamp()).append("\n");
        details.append("Direction: ").append(packet.getDirection()).append("\n");
        details.append("Packet Type: ").append(packet.getPacketType()).append("\n");
        details.append("Packet Name: ").append(packet.getPacketName()).append("\n");
        details.append("Size: ").append(packet.getSize()).append(" bytes\n\n");
        details.append("Content:\n");
        details.append(packet.getPacket().toString()).append("\n\n");

        packetDetailsArea.setText(details.toString());
    }

}