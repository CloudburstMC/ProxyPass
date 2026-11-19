package org.cloudburstmc.proxypass.ui.components;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.proxypass.Configuration;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.ui.UIPacketData;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SouthBarComponent extends JTabbedPane {
    private final List<Tab> tabs = new ArrayList<>();

    public SouthBarComponent(ProxyPass proxyPass, BedrockCodec codec, Configuration configuration, Consumer<Predicate<UIPacketData>> predicateConsumer, BiConsumer<Boolean, Set<String>> updateHandler) {
        this.tabs.add(new Filters(proxyPass, codec, configuration, predicateConsumer));
        this.tabs.add(new Blocks(codec, configuration, updateHandler));

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

    private static class Filters extends Tab {
        private final FilterComponent component;

        public Filters(ProxyPass proxyPass, BedrockCodec codec, Configuration configuration, Consumer<Predicate<UIPacketData>> predicateConsumer) {
            this.setLayout(new GridLayout(1, 1));
            this.component = new FilterComponent(proxyPass, codec, configuration, predicateConsumer);
        }

        @Override
        public void addContent() {
            this.add(this.component);
        }

        @Override
        public String getTitle() {
            return "Filters";
        }
    }

    private static class Blocks extends Tab {
        private final BlockComponent component;

        public Blocks(BedrockCodec codec, Configuration configuration, BiConsumer<Boolean, Set<String>> updateHandler) {
            this.setLayout(new GridLayout(1, 1));
            this.component = new BlockComponent(codec, configuration, updateHandler);
        }

        @Override
        public void addContent() {
            this.add(this.component);
        }

        @Override
        public String getTitle() {
            return "Blocking";
        }
    }

    private abstract static class Tab extends JPanel {
        public abstract void addContent();

        public abstract String getTitle();

        public void refresh() {
            this.removeAll();
            this.addContent();
            this.revalidate();
        }
    }
}
