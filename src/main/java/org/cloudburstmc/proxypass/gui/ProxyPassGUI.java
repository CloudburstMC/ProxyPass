package org.cloudburstmc.proxypass.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ProxyPassGUI extends Application {
    @Getter
    private static boolean enable = false;

    private static List<PacketInfo> capturedPackets = new CopyOnWriteArrayList<>();
    private static int packetCounter = 0;

    private TableView<PacketInfo> packetTable;
    private TextArea packetDetailsArea;
    private TextField filterTextField;
    private ComboBox<String> directionFilter;
    private Label statusLabel;
    private FilteredList<PacketInfo> filteredPackets;
    // 添加可观察集合作为表格数据源
    private ObservableList<PacketInfo> observablePackets;

    // 添加表格列标题中的筛选按钮引用
    private MenuButton directionFilterBtn;
    private MenuButton typeFilterBtn;

    // 添加用于存储选定包类型的集合
    private Set<String> selectedPacketTypes = new HashSet<>();

    // 添加用于跟踪已知包类型的集合
    private Set<String> knownPacketTypes = new HashSet<>();

    // 添加用于存储要忽略的包类型的集合
    public static Set<String> ignoredPacketTypes = new HashSet<>();

    // 添加标志，指示类型过滤器是否需要重建
    private boolean typeFilterNeedsRebuild = true;

    // 添加跟踪菜单是否打开的状态
    private boolean typeFilterMenuOpen = false;

    // 控制更新状态的频率
    private long lastStatusUpdateTime = 0;
    private static final long STATUS_UPDATE_INTERVAL = 500; // 更新间隔，毫秒

    public static void main(String[] args) {
        launch(args);
    }

    public static void addPacket(String direction, org.cloudburstmc.protocol.bedrock.packet.BedrockPacket packet, int size) {
        // 获取包类型名称
        String packetType = packet.getClass().getSimpleName();

        // 如果此类型的包在忽略列表中，则直接返回，不处理此包
        if (ignoredPacketTypes.contains(packetType)) {
            return;
        }

        PacketInfo packetInfo = new PacketInfo(++packetCounter, direction, packet, size);
        capturedPackets.add(packetInfo);

        Platform.runLater(() -> {
            if (instance != null) {
                // 直接更新可观察集合
                instance.observablePackets.add(packetInfo);

                // 限制状态更新频率
                instance.updateStatusThrottled();
            }
        });
    }

    private static ProxyPassGUI instance;

    @Override
    public void start(Stage primaryStage) {
        ProxyPassGUI.enable = true;
        instance = this;

        primaryStage.setTitle("ProxyPass - Minecraft Bedrock Protocol Analyzer");

        // Create the main layout
        BorderPane root = new BorderPane();

        // Top: Controls (搜索框和按钮)
        VBox topControls = createTopControls();
        root.setTop(topControls);

        // Center: Packet table
        packetTable = createPacketTable();
        root.setCenter(packetTable);

        // Bottom: Packet details
        packetDetailsArea = new TextArea();
        packetDetailsArea.setEditable(false);
        packetDetailsArea.setWrapText(true);
        packetDetailsArea.setPrefHeight(200);
        root.setBottom(packetDetailsArea);

        // Set up the scene
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        updateStatus();
    }

    private VBox createTopControls() {
        VBox controls = new VBox(10);
        controls.setPadding(new Insets(10));

        // 修改过滤控件为忽略包类型控件
        HBox filterControls = new HBox(10);

        Label filterLabel = new Label("ignored packet:");
        filterTextField = new TextField();
        filterTextField.setPrefWidth(200);
        filterTextField.setText(String.join(",", ignoredPacketTypes));
        filterTextField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateIgnoredPacketTypes(newVal);
            // TODO: 实现过滤已存在的包类型
//            applyFilters();
        });
        filterTextField.setPromptText("Enter exact packet types to ignore (e.g. LoginPacket,MovePacket)");

        // 创建方向过滤器，但不添加到UI中
        directionFilter = new ComboBox<>();
        directionFilter.getItems().addAll("All", "Client", "Server");
        directionFilter.setValue("All");
        directionFilter.setOnAction(e -> applyFilters());

        // 初始化选定的包类型集合，默认包含"All"
        selectedPacketTypes.add("All");

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> {
            capturedPackets.clear();
            packetCounter = 0;
            observablePackets.clear(); // 清空可观察集合
            knownPacketTypes.clear(); // 清空已知包类型集合
            typeFilterNeedsRebuild = true; // 标记需要重建类型过滤器
            applyFilters();
            packetDetailsArea.clear();
            updateStatus();
        });

        Button exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportPackets());

        HBox.setHgrow(filterTextField, Priority.ALWAYS);
        filterControls.getChildren().addAll(
                filterLabel, filterTextField,
                clearButton, exportButton
        );

        // Status bar
        HBox statusBar = new HBox(10);
        statusLabel = new Label("Ready");
        statusBar.getChildren().add(statusLabel);

        controls.getChildren().addAll(filterControls, statusBar);
        return controls;
    }

    // 更新要忽略的包类型列表
    private void updateIgnoredPacketTypes(String ignoredTypesText) {
        if (!ignoredTypesText.endsWith("Packet")) return;
        // 清空当前忽略列表
        ignoredPacketTypes.clear();

        // 解析输入文本，按逗号分割
        if (!ignoredTypesText.trim().isEmpty()) {
            String[] types = ignoredTypesText.split(",");
            for (String type : types) {
                String trimmed = type.trim();
                if (!trimmed.isEmpty()) {
                    ignoredPacketTypes.add(trimmed);
                }
            }
        }
    }

    private TableView<PacketInfo> createPacketTable() {
        TableView<PacketInfo> table = new TableView<>();

        // Create columns
        TableColumn<PacketInfo, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId()).asObject());
        idColumn.setPrefWidth(50);

        TableColumn<PacketInfo, String> timestampColumn = new TableColumn<>("Timestamp");
        timestampColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTimestamp()));
        timestampColumn.setPrefWidth(90);

        // 修改 Direction 列，添加筛选菜单
        TableColumn<PacketInfo, String> directionColumn = new TableColumn<>();
        directionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDirection()));
        directionColumn.setPrefWidth(130);

        // 创建自定义标题，包含标签和下拉菜单
        HBox directionHeader = new HBox(5);
        directionHeader.setAlignment(Pos.CENTER_LEFT);
        Label directionLabel = new Label("Direction");
        directionFilterBtn = new MenuButton("▼");
        directionFilterBtn.setStyle("-fx-font-size: 8pt;");

        // 添加菜单项
        updateDirectionFilterMenu();

        directionHeader.getChildren().addAll(directionLabel, directionFilterBtn);
        directionColumn.setGraphic(directionHeader);

        // 修改 Type 列，添加复选框筛选菜单
        TableColumn<PacketInfo, String> typeColumn = new TableColumn<>();
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPacketType()));
        typeColumn.setPrefWidth(200);

        // 创建自定义标题，包含标签和下拉菜单
        HBox typeHeader = new HBox(5);
        typeHeader.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label("Type");
        typeFilterBtn = new MenuButton("▼");
        typeFilterBtn.setStyle("-fx-font-size: 8pt;");

        // 添加菜单打开/关闭监听
        typeFilterBtn.setOnShowing(e -> {
            typeFilterMenuOpen = true;
            // 只在菜单打开时才重建类型过滤器
            if (typeFilterNeedsRebuild) {
                rebuildPacketTypeFilter();
                typeFilterNeedsRebuild = false;
            }
        });

        typeFilterBtn.setOnHiding(e -> {
            typeFilterMenuOpen = false;
        });

        typeHeader.getChildren().addAll(typeLabel, typeFilterBtn);
        typeColumn.setGraphic(typeHeader);

        TableColumn<PacketInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPacketName()));
        nameColumn.setPrefWidth(200);

        TableColumn<PacketInfo, Integer> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getSize()).asObject());
        sizeColumn.setPrefWidth(60);

        table.getColumns().addAll(idColumn, timestampColumn, directionColumn, typeColumn, nameColumn, sizeColumn);

        // 创建可观察集合并初始化它
        observablePackets = FXCollections.observableArrayList();
        // 将现有的数据包添加到可观察集合中
        observablePackets.addAll(capturedPackets);

        // 创建过滤列表
        filteredPackets = new FilteredList<>(observablePackets);
        table.setItems(filteredPackets);

        // Selection listener for packet details
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                displayPacketDetails(newSelection);
            }
        });

        return table;
    }

    // 更新方向筛选下拉菜单
    private void updateDirectionFilterMenu() {
        if (directionFilterBtn == null) return;

        directionFilterBtn.getItems().clear();

        // 添加菜单项
        MenuItem allItem = new MenuItem("All");
        allItem.setOnAction(e -> {
            directionFilter.setValue("All");
            applyFilters();
        });

        MenuItem clientItem = new MenuItem("Client");
        clientItem.setOnAction(e -> {
            directionFilter.setValue("Client");
            applyFilters();
        });

        MenuItem serverItem = new MenuItem("Server");
        serverItem.setOnAction(e -> {
            directionFilter.setValue("Server");
            applyFilters();
        });

        directionFilterBtn.getItems().addAll(allItem, clientItem, serverItem);

        // 设置按钮显示当前选择的筛选
        directionFilterBtn.setText(directionFilter.getValue() + " ▼");
    }

    // 节流更新状态函数，限制调用频率
    private void updateStatusThrottled() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStatusUpdateTime >= STATUS_UPDATE_INTERVAL) {
            updateStatus();
            lastStatusUpdateTime = currentTime;
        }
    }

    private void applyFilters() {
        String direction = directionFilter.getValue();

        Predicate<PacketInfo> filter = packet -> {
            // 方向过滤
            boolean matchesDirection = "All".equals(direction) ||
                    direction.equals(packet.getDirection());

            // 类型过滤 - 如果选择了"All"，或者选定的类型包含该包类型，则显示
            boolean matchesType = selectedPacketTypes.contains("All") ||
                    selectedPacketTypes.contains(packet.getPacketType());

            return matchesDirection && matchesType;
        };

        filteredPackets.setPredicate(filter);
        updateStatusWithFilter();

        // 更新筛选按钮的文本
        if (directionFilterBtn != null) {
            directionFilterBtn.setText(direction + " ▼");
        }

        if (typeFilterBtn != null) {
            int typeCount = selectedPacketTypes.size();
            String typeBtnText;

            if (selectedPacketTypes.contains("All")) {
                typeBtnText = "All";
            } else if (typeCount == 1) {
                typeBtnText = selectedPacketTypes.iterator().next();
            } else {
                typeBtnText = typeCount + " types";
            }

            typeFilterBtn.setText(typeBtnText + " ▼");
        }
    }

    private void displayPacketDetails(PacketInfo packet) {
        StringBuilder details = new StringBuilder();
        details.append("Packet ID: ").append(packet.getId()).append("\n");
        details.append("Timestamp: ").append(packet.getTimestamp()).append("\n");
        details.append("Direction: ").append(packet.getDirection()).append("\n");
        details.append("Packet Type: ").append(packet.getPacketType()).append("\n");
        details.append("Packet Name: ").append(packet.getPacketName()).append("\n");
        details.append("Size: ").append(packet.getSize()).append(" bytes\n\n");

        details.append("Content:\n");
        details.append(packet.getPacket().toString()).append("\n\n");

//        details.append("Raw Data:\n");
//        details.append(packet.getRawData());

        packetDetailsArea.setText(details.toString());
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
                    PacketFormatter.exportAsJson(file, filteredPackets);
                } else {
                    PacketFormatter.exportAsText(file, filteredPackets);
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

    private void updateStatus() {
        // 检查是否有新的包类型
        boolean hasNewTypes = checkForNewPacketTypes();

        // 只有在菜单打开时需要重建
        if (hasNewTypes && typeFilterMenuOpen) {
            rebuildPacketTypeFilter();
        } else if (hasNewTypes) {
            // 如果发现新类型但菜单未打开，则标记需要重建
            typeFilterNeedsRebuild = true;
        }

        // 始终更新状态栏
        updateStatusWithFilter();
    }

    // 检查是否有新的包类型
    private boolean checkForNewPacketTypes() {
        boolean hasNewTypes = false;

        // 收集当前所有包的类型
        Set<String> currentTypes = capturedPackets.stream()
                .map(PacketInfo::getPacketType)
                .collect(Collectors.toSet());

        // 检查是否有新类型
        for (String type : currentTypes) {
            if (!knownPacketTypes.contains(type)) {
                knownPacketTypes.add(type);
                hasNewTypes = true;
            }
        }

        return hasNewTypes;
    }

    // 完全重建类型过滤器菜单
    private void rebuildPacketTypeFilter() {
        // 保存当前选择状态
        Set<String> currentSelection = new HashSet<>(selectedPacketTypes);

        // 清空当前类型过滤菜单
        if (typeFilterBtn != null) {
            typeFilterBtn.getItems().clear();

            // 创建"All"选项的自定义菜单项
            CheckBox allCheckBox = new CheckBox("All");
            allCheckBox.setSelected(currentSelection.contains("All"));

            allCheckBox.setOnAction(e -> {
                if (allCheckBox.isSelected()) {
                    // 选择"All"时，清除其他所有选择
                    selectedPacketTypes.clear();
                    selectedPacketTypes.add("All");

                    // 更新所有复选框的状态
                    for (MenuItem item : typeFilterBtn.getItems()) {
                        if (item instanceof CustomMenuItem) {
                            Node content = ((CustomMenuItem) item).getContent();
                            if (content instanceof CheckBox && !((CheckBox) content).getText().equals("All")) {
                                ((CheckBox) content).setSelected(false);
                            }
                        }
                    }
                } else {
                    // 如果取消选择"All"，且没有其他选择，则重新选中"All"
                    if (selectedPacketTypes.size() <= 1) {
                        allCheckBox.setSelected(true);
                        selectedPacketTypes.clear();
                        selectedPacketTypes.add("All");
                    } else {
                        selectedPacketTypes.remove("All");
                    }
                }
                applyFilters();
            });

            CustomMenuItem allItem = new CustomMenuItem(allCheckBox);
            allItem.setHideOnClick(false);
            typeFilterBtn.getItems().add(allItem);

            // 添加分隔符
            typeFilterBtn.getItems().add(new SeparatorMenuItem());
        }

        // 按字母顺序排序类型
        List<String> sortedTypes = new ArrayList<>(knownPacketTypes);
        Collections.sort(sortedTypes);

        // 对于大量类型，考虑分组或限制显示数量
        int typeCount = sortedTypes.size();

        // 如果类型数量过大，使用分组或限制显示数量
        if (typeCount > 100) {
            // 添加搜索菜单项
            TextField searchField = new TextField();
            searchField.setPromptText("Search packet types...");

            CustomMenuItem searchItem = new CustomMenuItem(searchField);
            searchItem.setHideOnClick(false);
            typeFilterBtn.getItems().add(searchItem);
            typeFilterBtn.getItems().add(new SeparatorMenuItem());

            // 添加搜索功能
            searchField.textProperty().addListener((obs, oldText, newText) -> {
                // 从菜单中移除除了搜索框和"All"选项之外的所有项
                List<MenuItem> toKeep = new ArrayList<>();
                for (MenuItem item : typeFilterBtn.getItems()) {
                    if (item instanceof CustomMenuItem) {
                        Node content = ((CustomMenuItem) item).getContent();
                        if (content instanceof TextField ||
                                (content instanceof CheckBox && ((CheckBox) content).getText().equals("All"))) {
                            toKeep.add(item);
                        }
                    } else if (item instanceof SeparatorMenuItem) {
                        toKeep.add(item);
                    }
                }

                typeFilterBtn.getItems().clear();
                typeFilterBtn.getItems().addAll(toKeep);

                // 根据搜索条件添加匹配的类型
                String searchText = newText.toLowerCase();
                sortedTypes.stream()
                        .filter(type -> type.toLowerCase().contains(searchText))
                        .limit(50) // 限制显示数量
                        .forEach(type -> {
                            CheckBox typeCheckBox = createTypeCheckBox(type, currentSelection.contains(type));
                            CustomMenuItem typeItem = new CustomMenuItem(typeCheckBox);
                            typeItem.setHideOnClick(false);
                            typeFilterBtn.getItems().add(typeItem);
                        });
            });

            // 默认显示前50个类型
            sortedTypes.stream()
                    .limit(50)
                    .forEach(type -> {
                        CheckBox typeCheckBox = createTypeCheckBox(type, currentSelection.contains(type));
                        CustomMenuItem typeItem = new CustomMenuItem(typeCheckBox);
                        typeItem.setHideOnClick(false);
                        typeFilterBtn.getItems().add(typeItem);
                    });

            // 添加提示信息
            Label infoLabel = new Label("Showing first 50 of " + typeCount + " types. Use search to find others.");
            infoLabel.setStyle("-fx-font-style: italic; -fx-text-fill: grey;");
            CustomMenuItem infoItem = new CustomMenuItem(infoLabel);
            infoItem.setHideOnClick(false);
            typeFilterBtn.getItems().add(infoItem);
        } else {
            // 类型数量较少，全部显示
            for (String type : sortedTypes) {
                CheckBox typeCheckBox = createTypeCheckBox(type, currentSelection.contains(type));
                CustomMenuItem typeItem = new CustomMenuItem(typeCheckBox);
                typeItem.setHideOnClick(false);
                typeFilterBtn.getItems().add(typeItem);
            }
        }

        // 更新筛选按钮的文本
        updateTypeFilterButtonText();
    }

    // 创建类型复选框
    private CheckBox createTypeCheckBox(String type, boolean selected) {
        CheckBox typeCheckBox = new CheckBox(type);
        typeCheckBox.setSelected(selected);

        typeCheckBox.setOnAction(e -> {
            if (typeCheckBox.isSelected()) {
                // 添加到选定集合
                selectedPacketTypes.add(type);

                // 如果选中了具体类型，则移除"All"
                if (selectedPacketTypes.contains("All")) {
                    selectedPacketTypes.remove("All");

                    // 更新"All"复选框的状态
                    for (MenuItem item : typeFilterBtn.getItems()) {
                        if (item instanceof CustomMenuItem) {
                            Node content = ((CustomMenuItem) item).getContent();
                            if (content instanceof CheckBox && ((CheckBox) content).getText().equals("All")) {
                                ((CheckBox) content).setSelected(false);
                                break;
                            }
                        }
                    }
                }
            } else {
                // 从选定集合中移除
                selectedPacketTypes.remove(type);

                // 如果没有选定任何类型，则自动选中"All"
                if (selectedPacketTypes.isEmpty()) {
                    selectedPacketTypes.add("All");

                    // 更新"All"复选框的状态
                    for (MenuItem item : typeFilterBtn.getItems()) {
                        if (item instanceof CustomMenuItem) {
                            Node content = ((CustomMenuItem) item).getContent();
                            if (content instanceof CheckBox && ((CheckBox) content).getText().equals("All")) {
                                ((CheckBox) content).setSelected(true);
                                break;
                            }
                        }
                    }
                }
            }
            applyFilters();
        });

        return typeCheckBox;
    }

    // 更新类型过滤按钮文本
    private void updateTypeFilterButtonText() {
        if (typeFilterBtn != null) {
            int typeCount = selectedPacketTypes.size();
            String typeBtnText;

            if (selectedPacketTypes.contains("All")) {
                typeBtnText = "All";
            } else if (typeCount == 1) {
                typeBtnText = selectedPacketTypes.iterator().next();
            } else {
                typeBtnText = typeCount + " types";
            }

            typeFilterBtn.setText(typeBtnText + " ▼");
        }
    }

    private void updateStatusWithFilter() {
        int total = capturedPackets.size();
        int filtered = filteredPackets.size();

        if (total == filtered) {
            statusLabel.setText("Displaying all " + total + " packets");
        } else {
            statusLabel.setText("Displaying " + filtered + " of " + total + " packets");
        }
    }
}