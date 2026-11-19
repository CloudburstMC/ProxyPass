package org.cloudburstmc.proxypass.ui.components;

import lombok.Getter;
import lombok.SneakyThrows;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.ui.components.swing.JHintTextField;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.*;

public class PacketSelectListComponent extends JPanel {
    private final DefaultListModel<Item> fullModel = new DefaultListModel<>();

    @SneakyThrows
    public PacketSelectListComponent(BedrockCodec codec, Set<String> preconfiguredElements, boolean enabledByDefault, Runnable onChange) {
        this.setLayout(new GridBagLayout());

        DefaultListModel<Item> viewModel = new DefaultListModel<>();

        // Have mercy, please.
        Field field = codec.getClass().getDeclaredField("packetsByClass");
        field.setAccessible(true);
        Map<Class<? extends BedrockPacket>, BedrockPacketDefinition<? extends BedrockPacket>> packets = (Map<Class<? extends BedrockPacket>, BedrockPacketDefinition<? extends BedrockPacket>>) field.get(codec);
        for (Class<? extends BedrockPacket> aClass : packets.keySet()) {
            fullModel.addElement(new Item(
                    aClass,
                    preconfiguredElements.contains(aClass.getSimpleName()) != enabledByDefault,
                    onChange
            ));
        }

        for (int i = 0; i < fullModel.getSize(); i++) {
            viewModel.add(i, fullModel.getElementAt(i));
        }

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 0;

        JHintTextField filterField = new JHintTextField("Filter packets...");

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                handle();
            }

            public void removeUpdate(DocumentEvent e) {
                handle();
            }

            public void insertUpdate(DocumentEvent e) {
                handle();
            }

            private void handle() {
                String input = filterField.getText().trim().toLowerCase();
                viewModel.clear();
                for (int i = 0; i < fullModel.size(); i++) {
                    Item item = fullModel.getElementAt(i);
                    if (input.isEmpty() || item.toString().toLowerCase().contains(input)) {
                        viewModel.addElement(item);
                    }
                }
            }
        });
        this.add(filterField, c);

        c.fill = GridBagConstraints.BOTH;
        c.gridy = 1;
        c.weighty = 1;

        JList<Item> packetList = new JList<>(viewModel);
        packetList.setCellRenderer(new CheckListRenderer());
        packetList.setVisibleRowCount(8);
        packetList.setFixedCellHeight(22);
        packetList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = packetList.locationToIndex(e.getPoint());
                if (index < 0) return;
                Item item = viewModel.getElementAt(index);
                item.setSelected(!item.isSelected());
                packetList.repaint(packetList.getCellBounds(index, index));
            }
        });

        JScrollPane scrollPane = new JScrollPane(packetList);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        this.add(scrollPane, c);
    }

    public Set<Class<? extends BedrockPacket>> getSelectedPackets() {
        Set<Class<? extends BedrockPacket>> selectedPackets = new HashSet<>();
        for (int i = 0; i < fullModel.getSize(); i++) {
            Item item = fullModel.get(i);
            if (item.isSelected()) selectedPackets.add(item.getPacket());
        }
        return Collections.unmodifiableSet(selectedPackets);
    }

    private static class Item {
        @Getter
        private final Class<? extends BedrockPacket> packet;
        private final Runnable onChange;
        @Getter
        private boolean selected;

        public Item(Class<? extends BedrockPacket> packet, boolean selected, Runnable onChange) {
            this.packet = packet;
            this.selected = selected;
            this.onChange = onChange;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            this.onChange.run();
        }

        @Override
        public String toString() {
            return packet.getSimpleName();
        }
    }

    private static class CheckListRenderer extends JCheckBox implements ListCellRenderer<Item> {
        public CheckListRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Item> list,
                                                      Item item, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setEnabled(list.isEnabled());
            setSelected(item.isSelected());
            setText(item.toString());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }
}
