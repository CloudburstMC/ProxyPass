package org.cloudburstmc.proxypass.ui.components.swing;

import lombok.SneakyThrows;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class SearchableJList<T> extends JPanel {
    private final DefaultListModel<T> fullModel = new DefaultListModel<>();
    private final DefaultListModel<T> viewModel = new DefaultListModel<>();
    private final JHintTextField filterField;

    @SneakyThrows
    public SearchableJList(String placeholderText) {
        this.setLayout(new GridBagLayout());

        for (int i = 0; i < fullModel.getSize(); i++) {
            viewModel.add(i, fullModel.getElementAt(i));
        }

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 0;

        filterField = new JHintTextField(placeholderText);

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void insertUpdate(DocumentEvent e) {
                update();
            }
        });
        this.add(filterField, c);

        c.fill = GridBagConstraints.BOTH;
        c.gridy = 1;
        c.weighty = 1;

        JList<T> packetList = new JList<>(viewModel);
        packetList.setVisibleRowCount(8);
        packetList.setFixedCellHeight(22);

        JScrollPane scrollPane = new JScrollPane(packetList);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        this.add(scrollPane, c);
    }

    public void add(T element) {
        fullModel.addElement(element);
        update();
    }

    public void addAll(List<T> elements) {
        fullModel.addAll(elements);
        update();
    }

    public void remove(T element) {
        fullModel.removeElement(element);
        update();
    }

    public void removeAll(List<T> elements) {
        elements.forEach(fullModel::removeElement);
        update();
    }

    public void clear() {
        fullModel.clear();
        viewModel.clear();
    }

    public List<T> items() {
        List<T> items = new ArrayList<>();

        for (Enumeration<T> e = this.fullModel.elements(); e.hasMoreElements();) {
            items.add(e.nextElement());
        }

        return items;
    }

    private void update() {
        String input = filterField.getText().trim().toLowerCase();
        viewModel.clear();
        for (int i = 0; i < fullModel.size(); i++) {
            Object item = fullModel.getElementAt(i);
            if (input.isEmpty() || item.toString().toLowerCase().contains(input)) {
                viewModel.addElement((T) item);
            }
        }
    }
}
