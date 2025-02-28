package org.cloudburstmc.proxypass.gui.component;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.cloudburstmc.proxypass.gui.util.FilterManager;

public class StatusBarComponent {
    private final HBox statusBar;
    private final Label statusLabel;
    private final PacketTableComponent tableComponent;

    // 控制更新状态的频率
    private long lastStatusUpdateTime = 0;
    private static final long STATUS_UPDATE_INTERVAL = 500; // 更新间隔，毫秒

    public StatusBarComponent(PacketTableComponent tableComponent) {
        this.tableComponent = tableComponent;
        statusBar = new HBox(10);
        statusLabel = new Label("Ready");
        statusBar.getChildren().add(statusLabel);
    }

    public Node getNode() {
        return statusBar;
    }

    public void updateStatus() {
        int total = getTotalPacketCount();
        int filtered = getFilteredPacketCount();

        if (total == filtered) {
            statusLabel.setText("Displaying all " + total + " packets");
        } else {
            statusLabel.setText("Displaying " + filtered + " of " + total + " packets");
        }
    }

    public void updateStatusThrottled() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatusUpdateTime >= STATUS_UPDATE_INTERVAL) {
            updateStatus();
            lastStatusUpdateTime = currentTime;
        }
    }

    private int getTotalPacketCount() {
        // 使用表格组件获取总数据包数量
        return tableComponent.getTotalPacketCount();
    }

    private int getFilteredPacketCount() {
        // 使用表格组件获取过滤后数据包数量
        return tableComponent.getFilteredPacketCount();
    }
}