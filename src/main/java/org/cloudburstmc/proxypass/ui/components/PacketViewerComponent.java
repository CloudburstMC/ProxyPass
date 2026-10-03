package org.cloudburstmc.proxypass.ui.components;
import com.google.gson.*;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.ui.UIPacketData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class PacketViewerComponent extends JTabbedPane {
    private final List<Tab> tabs = new ArrayList<>();

    private UIPacketData currentPacket;

    public PacketViewerComponent() {
        this.tabs.add(new Details());
        this.tabs.add(new Json());
        this.tabs.add(new Hex());

        for (Tab tab : this.tabs) {
            tab.refresh();
            tab.setMaximumSize(tab.getSize());
            JScrollPane scrollPane = new JScrollPane(tab);
            scrollPane.getVerticalScrollBar().setUnitIncrement(20);
            this.add(tab.getTitle(), scrollPane);
        }

        this.setMinimumSize(this.getSize());
        this.setMaximumSize(this.getSize());
    }

    public void setCurrentPacket(UIPacketData currentPacket) {
        if (this.currentPacket == null && currentPacket == null) return;
        else if (this.currentPacket != null && currentPacket != null) {
            if (this.currentPacket.equals(currentPacket)) return;
        }
        this.currentPacket = currentPacket;

        this.tabs.forEach(Tab::refresh);
    }

    public class Details extends Tab {
        public Details() {
            this.setLayout(new GridLayout(1, 1));
        }

        @Override
        public void addContent() {
            UIPacketData packet = PacketViewerComponent.this.currentPacket;
            BedrockPacketDefinition<?> definition = ProxyPass.CODEC.getPacketDefinition(packet.packet().getClass());

            Map<String, String> data = new LinkedHashMap<>();
            data.put("Packet Name", packet.packet().getClass().getSimpleName());
            data.put("Packet ID", String.valueOf(packet.packetId()));
            data.put("Time Captured", packet.timeCaptured().toString());
            data.put("Direction", packet.direction().name());
            data.put("Serializer", definition.getSerializer().getClass().getName());

            JTextArea area = new JTextArea(data.entrySet().stream().map(e -> e.getKey() + ": " + e.getValue()).collect(Collectors.joining("\n")));
            area.setEditable(false);
            this.add(area);
        }

        @Override
        public String getTitle() {
            return "Details";
        }
    }

    public class Json extends Tab {
        public Json() {
            this.setLayout(new BorderLayout());
        }

        @Override
        public void addContent() {
            UIPacketData packet = PacketViewerComponent.this.currentPacket;

            JsonElement root;
            try {
                root = JsonParser.parseString(packet.jsonString());
            } catch (JsonSyntaxException e) {
                // Not valid JSON (e.g. the "failed to JSONify" placeholder) - just show it as-is.
                JTextArea area = new JTextArea(packet.jsonString());
                area.setLineWrap(true);
                area.setEditable(false);
                this.add(area, BorderLayout.CENTER);
                return;
            }

            JTree tree = new JTree(new JsonTreeNode(null, null, root));
            tree.setRootVisible(true);
            tree.setShowsRootHandles(true);
            tree.setRowHeight(18);
            tree.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            tree.setCellRenderer(new JsonCellRenderer());
            tree.setBackground(Color.decode("#1E1F22"));
            // Reveal the top level only; everything below stays folded until asked for.
            tree.expandRow(0);

            JButton expandAll = new JButton("Expand all");
            expandAll.addActionListener(e -> setExpandedRecursively(tree, new TreePath(tree.getModel().getRoot()), true));
            JButton collapseAll = new JButton("Collapse all");
            collapseAll.addActionListener(e -> setExpandedRecursively(tree, new TreePath(tree.getModel().getRoot()), false));

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            toolbar.add(expandAll);
            toolbar.add(collapseAll);

            this.add(toolbar, BorderLayout.NORTH);
            this.add(new JScrollPane(tree), BorderLayout.CENTER);
        }

        @Override
        public String getTitle() {
            return "JSON";
        }

        private void setExpandedRecursively(JTree tree, TreePath path, boolean expand) {
            TreeNode node = (TreeNode) path.getLastPathComponent();
            if (node.getChildCount() > 0) {
                for (Enumeration<? extends TreeNode> children = node.children(); children.hasMoreElements(); ) {
                    setExpandedRecursively(tree, path.pathByAddingChild(children.nextElement()), expand);
                }
            }
            if (expand) {
                tree.expandPath(path);
            } else if (path.getParentPath() != null) {
                tree.collapsePath(path);
            }
        }

        private static final class JsonTreeNode extends DefaultMutableTreeNode {
            private final String key;
            private final Integer index;
            private final JsonElement element;

            JsonTreeNode(String key, Integer index, JsonElement element) {
                this.key = key;
                this.index = index;
                this.element = element;

                if (element.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        this.add(new JsonTreeNode(entry.getKey(), null, entry.getValue()));
                    }
                } else if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    for (int i = 0; i < array.size(); i++) {
                        this.add(new JsonTreeNode(null, i, array.get(i)));
                    }
                }
            }
        }

        private static final class JsonCellRenderer extends DefaultTreeCellRenderer {
            private static final String KEY_COLOR = "#AC6EBA";
            private static final String STRING_COLOR = "#519572";
            private static final String NUMBER_COLOR = "#29AB89";
            private static final String BOOLEAN_COLOR = "#CE7C54";
            private static final String NULL_COLOR = "#CE7C54";
            private static final String PUNCTUATION_COLOR = "#888888";
            private static final String MUTED_COLOR = "#999999";

            JsonCellRenderer() {
                setLeafIcon(null);
                setOpenIcon(null);
                setClosedIcon(null);
                setBackgroundNonSelectionColor(Color.decode("#1E1F22"));
                setBackgroundSelectionColor(Color.decode("#26282E"));
                setBorderSelectionColor(null);
                setTextNonSelectionColor(Color.WHITE);
                setTextSelectionColor(Color.WHITE);
            }

            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                            boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof JsonTreeNode node) {
                    setText(render(node, expanded));
                }
                return this;
            }

            private String render(JsonTreeNode node, boolean expanded) {
                StringBuilder html = new StringBuilder("<html><nobr>");

                if (node.index != null) {
                    html.append(span(MUTED_COLOR, node.index + ": "));
                } else if (node.key != null) {
                    html.append(span(KEY_COLOR, "\"" + escape(node.key) + "\""))
                            .append(span(PUNCTUATION_COLOR, ": "));
                }

                JsonElement element = node.element;
                if (element.isJsonObject()) {
                    appendContainer(html, expanded, element.getAsJsonObject().size(), "{", "}", "key");
                } else if (element.isJsonArray()) {
                    appendContainer(html, expanded, element.getAsJsonArray().size(), "[", "]", "item");
                } else if (element.isJsonNull()) {
                    html.append(span(NULL_COLOR, "null"));
                } else {
                    JsonPrimitive primitive = element.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        html.append(span(STRING_COLOR, "\"" + escape(primitive.getAsString()) + "\""));
                    } else if (primitive.isBoolean()) {
                        html.append(span(BOOLEAN_COLOR, String.valueOf(primitive.getAsBoolean())));
                    } else {
                        html.append(span(NUMBER_COLOR, primitive.getAsString()));
                    }
                }

                html.append("</nobr></html>");
                return html.toString();
            }

            private void appendContainer(StringBuilder html, boolean expanded, int size, String open, String close, String noun) {
                if (size == 0) {
                    html.append(span(PUNCTUATION_COLOR, open + close));
                } else if (expanded) {
                    html.append(span(PUNCTUATION_COLOR, open));
                } else {
                    html.append(span(PUNCTUATION_COLOR, open + "…" + close))
                            .append(' ')
                            .append(span(MUTED_COLOR, size + " " + noun + (size == 1 ? "" : "s")));
                }
            }

            private static String span(String color, String text) {
                return "<span style='color:" + color + "'>" + text + "</span>";
            }

            private static String escape(String text) {
                return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            }
        }
    }

    public class Hex extends Tab {
        public Hex() {
            this.setLayout(new BorderLayout());
        }

        @Override
        public void addContent() {
            JTable table = new JTable(new ByteTableModel(currentPacket.rawBytes()));
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

            JScrollPane pane = new JScrollPane(table);
            this.add(pane, BorderLayout.CENTER);

            JTextArea area = new JTextArea(new String(currentPacket.rawBytes()));
            area.setEditable(false);

            this.add(area, BorderLayout.EAST);
        }

        @Override
        public String getTitle() {
            return "Hex";
        }

        @Override
        public void addMissingLabel(JLabel label) {
            this.add(label, BorderLayout.CENTER);
        }

        private static class ByteTableModel extends AbstractTableModel {
            private final List<String> headers = List.of(
                    "  ", "00", "01", "02", "03", "04", "05",
                    "06", "07", "08", "09", "0A", "0B", "0C", "0D",
                    "0E", "0F"
            );
            private final byte[] bytes;

            public ByteTableModel(byte[] bytes) {
                this.bytes = bytes;
            }

            public String getColumnName(int column) {
                return headers.get(column);
            }

            public int getRowCount() {
                return (int) Math.ceil(bytes.length / 16f);
            }

            public int getColumnCount() {
                return Math.min(16, bytes.length) + 1;
            }

            public Object getValueAt(int row, int col) {
                if (col == 0) {
                    String display = Integer.toString(row * 16, 16).toUpperCase();
                    if (display.length() < 8) display = "0".repeat( 8 - display.length()) + display;
                    return display;
                }
                int index = (col - 1) + (row * 16);
                if (index >= bytes.length) return "";
                String display = Integer.toString(bytes[index] & 0xFF, 16).toUpperCase();
                if (display.length() == 1) display = "0" + display;
                return display;
            }
        }
    }

    private abstract class Tab extends JPanel {
        public abstract void addContent();

        public abstract String getTitle();

        public void refresh() {
            this.removeAll();
            if (PacketViewerComponent.this.currentPacket == null) {
                JLabel label = new JLabel("Select a packet to get more information.");
                label.setFont(label.getFont().deriveFont(18f));
                this.addMissingLabel(label);
            } else {
                this.addContent();
            }
            this.revalidate();
        }

        public void addMissingLabel(JLabel label) {
            this.add(label);
        }
    }
}
