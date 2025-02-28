package org.cloudburstmc.proxypass.gui.util;

import javafx.scene.Node;
import javafx.scene.control.*;
import org.cloudburstmc.proxypass.gui.component.PacketTableComponent;
import org.cloudburstmc.proxypass.gui.model.PacketInfo;

import java.util.*;
import java.util.function.Predicate;

public class FilterManager {
    private static FilterManager instance;

    private String directionFilter = "All";
    private Set<String> selectedPacketTypes = new HashSet<>(Collections.singleton("All"));
    private Set<String> knownPacketTypes = new HashSet<>();
    private boolean typeFilterNeedsRebuild = true;
    private boolean typeFilterMenuOpen = false;

    private FilterManager() {
    }

    public static FilterManager getInstance() {
        if (instance == null) {
            instance = new FilterManager();
        }
        return instance;
    }

    public String getDirectionFilter() {
        return directionFilter;
    }

    public void setDirectionFilter(String direction) {
        this.directionFilter = direction;
    }

    public Set<String> getSelectedPacketTypes() {
        return selectedPacketTypes;
    }

    public Set<String> getKnownPacketTypes() {
        return knownPacketTypes;
    }

    public void clearKnownPacketTypes() {
        knownPacketTypes.clear();
        typeFilterNeedsRebuild = true;
    }

    public void clearFilters() {
        selectedPacketTypes.clear();
        selectedPacketTypes.add("All");
        directionFilter = "All";
    }

    public Predicate<PacketInfo> createFilterPredicate() {
        return packet -> {
            // 方向过滤
            boolean matchesDirection = "All".equals(directionFilter) ||
                    directionFilter.equals(packet.getDirection());

            // 类型过滤
            boolean matchesType = selectedPacketTypes.contains("All") ||
                    selectedPacketTypes.contains(packet.getPacketType());

            return matchesDirection && matchesType;
        };
    }

    /**
     * check if a new packet type is found
     * @param packetType PacketInfo#getPacketType
     * @return true if a new packet type is found
     */
    public boolean checkForNewPacketTypes(String packetType) {
        if (knownPacketTypes.contains(packetType)) return false;
        knownPacketTypes.add(packetType);
        typeFilterNeedsRebuild = true;
        return true;
    }

    public String getTypeFilterButtonText() {
        int typeCount = selectedPacketTypes.size();
        if (selectedPacketTypes.contains("All")) {
            return "All";
        } else if (typeCount == 1) {
            return selectedPacketTypes.iterator().next();
        } else {
            return typeCount + " types";
        }
    }

    public void rebuildPacketTypeFilter(MenuButton typeFilterBtn) {
        if (!typeFilterNeedsRebuild && typeFilterMenuOpen) return;

        typeFilterMenuOpen = true;

        // 保存当前选择状态
        Set<String> currentSelection = new HashSet<>(selectedPacketTypes);

        // 清空当前类型过滤菜单
        typeFilterBtn.getItems().clear();

        // 创建"All"选项
        CheckBox allCheckBox = new CheckBox("All");
        allCheckBox.setSelected(currentSelection.contains("All"));
        allCheckBox.setOnAction(e -> handleAllCheckBoxAction(allCheckBox, typeFilterBtn));

        CustomMenuItem allItem = new CustomMenuItem(allCheckBox);
        allItem.setHideOnClick(false);
        typeFilterBtn.getItems().add(allItem);

        // 添加分隔符
        typeFilterBtn.getItems().add(new SeparatorMenuItem());

        // 按字母顺序排序类型
        List<String> sortedTypes = new ArrayList<>(knownPacketTypes);
        Collections.sort(sortedTypes);

        // 添加类型过滤器
        if (sortedTypes.size() > 60) {
            addSearchableTypeFilters(typeFilterBtn, sortedTypes, currentSelection);
        } else {
            addAllTypeFilters(typeFilterBtn, sortedTypes, currentSelection);
        }

        // 设置菜单关闭监听
        typeFilterBtn.setOnHidden(e -> typeFilterMenuOpen = false);

        typeFilterNeedsRebuild = false;
    }

    private void handleAllCheckBoxAction(CheckBox allCheckBox, MenuButton typeFilterBtn) {
        if (allCheckBox.isSelected()) {
            // 选择"All"时，清除其他所有选择
            selectedPacketTypes.clear();
            selectedPacketTypes.add("All");

            // 更新所有复选框状态
            updateAllCheckboxes(typeFilterBtn, false);
        } else {
            // 如果取消选择"All"且没有其他选择，则恢复选中"All"
            if (selectedPacketTypes.size() <= 1) {
                allCheckBox.setSelected(true);
                selectedPacketTypes.clear();
                selectedPacketTypes.add("All");
            } else {
                selectedPacketTypes.remove("All");
            }
        }
        PacketTableComponent.getInstance().applyFilters();
    }

    private void updateAllCheckboxes(MenuButton typeFilterBtn, boolean selected) {
        for (MenuItem item : typeFilterBtn.getItems()) {
            if (item instanceof CustomMenuItem) {
                Node content = ((CustomMenuItem) item).getContent();
                if (content instanceof CheckBox && !((CheckBox) content).getText().equals("All")) {
                    ((CheckBox) content).setSelected(selected);
                }
            }
        }
    }

    private void addSearchableTypeFilters(MenuButton typeFilterBtn, List<String> types, Set<String> currentSelection) {
        // 添加搜索框
        TextField searchField = new TextField();
        searchField.setPromptText("Search packet types...");
        CustomMenuItem searchItem = new CustomMenuItem(searchField);
        searchItem.setHideOnClick(false);
        typeFilterBtn.getItems().add(searchItem);
        typeFilterBtn.getItems().add(new SeparatorMenuItem());

        // 设置搜索功能
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            // 保留搜索框和All选项
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

            // 清除并重新添加保留项
            typeFilterBtn.getItems().clear();
            typeFilterBtn.getItems().addAll(toKeep);

            // 添加匹配的类型
            String searchText = newText.toLowerCase();
            types.stream()
                    .filter(type -> type.toLowerCase().contains(searchText))
                    .limit(50)
                    .forEach(type -> addTypeCheckBox(typeFilterBtn, type, currentSelection.contains(type)));
        });

        // 默认显示前50个
        types.stream()
                .limit(50)
                .forEach(type -> addTypeCheckBox(typeFilterBtn, type, currentSelection.contains(type)));

        // 添加信息提示
        Label infoLabel = new Label("Showing first 50 of " + types.size() + " types. Use search to find others.");
        infoLabel.setStyle("-fx-font-style: italic; -fx-text-fill: grey;");
        CustomMenuItem infoItem = new CustomMenuItem(infoLabel);
        infoItem.setHideOnClick(false);
        typeFilterBtn.getItems().add(infoItem);
    }

    private void addAllTypeFilters(MenuButton typeFilterBtn, List<String> types, Set<String> currentSelection) {
        for (String type : types) {
            addTypeCheckBox(typeFilterBtn, type, currentSelection.contains(type));
        }
    }

    private void addTypeCheckBox(MenuButton typeFilterBtn, String type, boolean selected) {
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
            PacketTableComponent.getInstance().applyFilters();
        });

        CustomMenuItem typeItem = new CustomMenuItem(typeCheckBox);
        typeItem.setHideOnClick(false);
        typeFilterBtn.getItems().add(typeItem);
    }
}