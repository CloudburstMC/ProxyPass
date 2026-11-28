package org.cloudburstmc.proxypass.ui.components;

import lombok.extern.slf4j.Slf4j;
import org.cloudburstmc.proxypass.ui.UIPacketData;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Slf4j
public class PacketListComponent extends JTable {
    private final List<UIPacketData> items = new CopyOnWriteArrayList<>();
    private Predicate<UIPacketData> filter = packet -> true;
    private int count = 0;

    public PacketListComponent(Consumer<UIPacketData> selectionHandler) {
        setModel(new PacketTableModel());
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        this.getSelectionModel().addListSelectionListener(e -> {
            UIPacketData data = fromVisualIndex(this.getSelectedRow());
            selectionHandler.accept(data);
        });
    }

    public void newPacket(UIPacketData data) {
        this.items.add(0, data);

        if (filter.test(data)) {
            count++;
            ((PacketTableModel) this.getModel()).fireTableRowsInserted(0, 0);
        }
    }

    public void addFilter(Predicate<UIPacketData> filter) {
        this.filter = filter;

        count = 0;
        this.items.forEach(item -> {
            if (filter.test(item)) count++;
        });

        ((PacketTableModel) this.getModel()).fireTableDataChanged();
    }

    public UIPacketData fromVisualIndex(int index) {
        int visualIndex = 0;
        for (UIPacketData data : this.items) {
            if (filter.test(data)) {
                if (visualIndex == index) return data;
                visualIndex++;
            }
        }

        return null;
    }

    private class PacketTableModel extends AbstractTableModel {
        private final List<String> headers = List.of(
                "Packet Name", "Direction", "ID", "Size"
        );

        public String getColumnName(int column) {
            return headers.get(column);
        }

        public int getRowCount() {
            return count;
        }

        public int getColumnCount() {
            return headers.size();
        }

        public Object getValueAt(int row, int col) {
            UIPacketData packet = fromVisualIndex(row);
            return switch (col) {
                case 0 -> packet.packet().getClass().getSimpleName();
                case 1 -> packet.direction().name();
                case 2 -> packet.packetId();
                case 3 -> packet.rawBytes().length;
                default -> throw new IllegalArgumentException("Invalid column given to PacketTableModel");
            };
        }
    }
}
