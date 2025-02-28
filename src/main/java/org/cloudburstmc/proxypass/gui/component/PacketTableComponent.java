package org.cloudburstmc.proxypass.gui.component;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.cloudburstmc.proxypass.gui.model.PacketInfo;
import org.cloudburstmc.proxypass.gui.util.FilterManager;

import java.util.List;
import java.util.function.Consumer;

public class PacketTableComponent {
    private final TableView<PacketInfo> packetTable;
    private final ObservableList<PacketInfo> observablePackets;
    private final FilteredList<PacketInfo> filteredPackets;

    private MenuButton directionFilterBtn;
    private MenuButton typeFilterBtn;

    private static PacketTableComponent instance;

    public PacketTableComponent(List<PacketInfo> initialPackets, Consumer<PacketInfo> selectionHandler) {
        // 创建表格和数据源
        packetTable = new TableView<>();
        observablePackets = FXCollections.observableArrayList();
        observablePackets.addAll(initialPackets);
        filteredPackets = new FilteredList<>(observablePackets);

        // 创建表格列
        createTableColumns();

        // 设置表格数据
        packetTable.setItems(filteredPackets);

        // 添加选择监听器
        packetTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSelection, newSelection) -> {
                    if (newSelection != null) {
                        selectionHandler.accept(newSelection);
                    }
                }
        );
        instance = this;
    }

    public static PacketTableComponent getInstance() {
        return instance;
    }

    public Node getNode() {
        return packetTable;
    }

    public void addPacket(PacketInfo packet) {
        observablePackets.add(packet);
    }

    public void clearPackets() {
        observablePackets.clear();
        FilterManager.getInstance().clearFilters();
        applyFilters();
    }

    private void createTableColumns() {
        TableColumn<PacketInfo, Integer> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getId()).asObject());
        idColumn.setPrefWidth(50);

        TableColumn<PacketInfo, String> timestampColumn = new TableColumn<>("Timestamp");
        timestampColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTimestamp()));
        timestampColumn.setPrefWidth(90);

        // 方向列
        TableColumn<PacketInfo, String> directionColumn = new TableColumn<>();
        directionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDirection()));
        directionColumn.setPrefWidth(130);

        HBox directionHeader = new HBox(5);
        directionHeader.setAlignment(Pos.CENTER_LEFT);
        Label directionLabel = new Label("Direction");
        directionFilterBtn = new MenuButton("▼");
        directionFilterBtn.setStyle("-fx-font-size: 8pt;");
        updateDirectionFilterMenu();
        directionHeader.getChildren().addAll(directionLabel, directionFilterBtn);
        directionColumn.setGraphic(directionHeader);

        // 类型列
        TableColumn<PacketInfo, String> typeColumn = new TableColumn<>();
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPacketType()));
        typeColumn.setPrefWidth(200);

        HBox typeHeader = new HBox(5);
        typeHeader.setAlignment(Pos.CENTER_LEFT);
        Label typeLabel = new Label("Type");
        typeFilterBtn = new MenuButton("▼");
        typeFilterBtn.setStyle("-fx-font-size: 8pt;");
        typeFilterBtn.setText("All");
        setupTypeFilterMenu();
        typeHeader.getChildren().addAll(typeLabel, typeFilterBtn);
        typeColumn.setGraphic(typeHeader);

        // 名称和大小列
        TableColumn<PacketInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPacketName()));
        nameColumn.setPrefWidth(200);

        TableColumn<PacketInfo, Integer> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getSize()).asObject());
        sizeColumn.setPrefWidth(60);

        packetTable.getColumns().addAll(idColumn, timestampColumn, directionColumn, typeColumn, nameColumn, sizeColumn);
    }

    private void updateDirectionFilterMenu() {
        if (directionFilterBtn == null) return;
        directionFilterBtn.getItems().clear();

        MenuItem allItem = new MenuItem("All");
        allItem.setOnAction(e -> {
            FilterManager.getInstance().setDirectionFilter("All");
            applyFilters();
        });

        MenuItem clientItem = new MenuItem("Client");
        clientItem.setOnAction(e -> {
            FilterManager.getInstance().setDirectionFilter("Client");
            applyFilters();
        });

        MenuItem serverItem = new MenuItem("Server");
        serverItem.setOnAction(e -> {
            FilterManager.getInstance().setDirectionFilter("Server");
            applyFilters();
        });

        directionFilterBtn.getItems().addAll(allItem, clientItem, serverItem);
        directionFilterBtn.setText(FilterManager.getInstance().getDirectionFilter());
    }

    private void setupTypeFilterMenu() {
        typeFilterBtn.setOnShowing(e -> FilterManager.getInstance().rebuildPacketTypeFilter(typeFilterBtn));
    }

    public void applyFilters() {
        filteredPackets.setPredicate(FilterManager.getInstance().createFilterPredicate());

        if (directionFilterBtn != null) {
            directionFilterBtn.setText(FilterManager.getInstance().getDirectionFilter());
        }

        if (typeFilterBtn != null) {
            typeFilterBtn.setText(FilterManager.getInstance().getTypeFilterButtonText());
        }
    }

    public int getFilteredPacketCount() {
        return filteredPackets.size();
    }

    public int getTotalPacketCount() {
        return observablePackets.size();
    }

    public FilteredList<PacketInfo> getFilteredPackets() {
        return filteredPackets;
    }

    public TableView<PacketInfo> getPacketTable() {
        return packetTable;
    }
}