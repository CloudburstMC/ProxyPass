package org.cloudburstmc.proxypass.gui.component;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.cloudburstmc.proxypass.gui.ProxyPassGUI;
import org.cloudburstmc.proxypass.gui.util.PacketFormatter;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FilterComponent {
    private final VBox controlsContainer;
    private final TextField ignoredPacketsField;
    private final PacketTableComponent tableComponent;

    private final CheckBox autoScrollCheckBox;

    public FilterComponent(PacketTableComponent tableComponent, Runnable clearAction) {
        this.tableComponent = tableComponent;
        controlsContainer = new VBox(10);
        controlsContainer.setPadding(new Insets(10));

        // 创建过滤器控件
        HBox filterControls = new HBox(10);

        Label filterLabel = new Label("Ignored packet:");
        ignoredPacketsField = new TextField();
        ignoredPacketsField.setPrefWidth(200);
        ignoredPacketsField.setText(String.join(",", ProxyPassGUI.ignoredPacketTypes));
        ignoredPacketsField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateIgnoredPacketTypes(newVal);
        });
        ignoredPacketsField.setPromptText("Enter exact packet types to ignore (e.g. LoginPacket,MovePacket)");

        autoScrollCheckBox = new CheckBox("Auto-scroll");
        autoScrollCheckBox.setSelected(true);
        autoScrollCheckBox.setTooltip(new Tooltip("Automatically scroll to the newest packets"));

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearAction.run());

        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportPackets());

        HBox.setHgrow(ignoredPacketsField, Priority.ALWAYS);
        filterControls.getChildren().addAll(
                filterLabel, ignoredPacketsField,
                autoScrollCheckBox,
                clearButton, exportButton
        );

        controlsContainer.getChildren().add(filterControls);
    }

    public Node getNode() {
        return controlsContainer;
    }

    public void addNode(Node node) {
        controlsContainer.getChildren().add(node);
    }

    public void scrollToBottomIfEnabled() {
        if (autoScrollCheckBox.isSelected() && !tableComponent.getPacketTable().getItems().isEmpty()) {
            tableComponent.getPacketTable().scrollTo(tableComponent.getPacketTable().getItems().size() - 1);
            tableComponent.getPacketTable().getSelectionModel().select(tableComponent.getPacketTable().getItems().size() - 1);
        }
    }

    private void updateIgnoredPacketTypes(String ignoredTypesText) {
        // 验证文本格式
        if (!ignoredTypesText.trim().isEmpty() && !ignoredTypesText.endsWith("Packet")) {
            return;
        }

        // 清空当前忽略列表
        ProxyPassGUI.ignoredPacketTypes.clear();

        // 解析输入文本，按逗号分割
        if (!ignoredTypesText.trim().isEmpty()) {
            String[] types = ignoredTypesText.split(",");
            for (String type : types) {
                String trimmed = type.trim();
                if (!trimmed.isEmpty()) {
                    ProxyPassGUI.ignoredPacketTypes.add(trimmed);
                }
            }
        }
    }

    private void exportPackets() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Packets");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );

        LocalDateTime now = LocalDateTime.now();
        String defaultFilename = "proxypass_export_" +
                now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setInitialFileName(defaultFilename);

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                if (file.getName().endsWith(".json")) {
                    PacketFormatter.exportAsJson(file, tableComponent.getFilteredPackets());
                } else {
                    PacketFormatter.exportAsText(file, tableComponent.getFilteredPackets());
                }

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText(null);
                alert.setContentText("Packets exported successfully to: " + file.getAbsolutePath());
                alert.showAndWait();
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText(null);
                alert.setContentText("Failed to export packets: " + e.getMessage());
                alert.showAndWait();
            }
        }
    }
}