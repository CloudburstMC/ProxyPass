package org.cloudburstmc.proxypass.ui.components;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.Configuration;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.ui.UIPacketData;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BlockComponent extends JPanel {
    private final BiConsumer<Boolean, Set<String>> updateHandler;
    private final PacketSelectListComponent packetSelectListComponent;
    private final JCheckBox blockResourcePacks;

    public BlockComponent(BedrockCodec codec, Configuration configuration, BiConsumer<Boolean, Set<String>> updateHandler) {
        this.setLayout(new BorderLayout());
        this.updateHandler = updateHandler;

        packetSelectListComponent = new PacketSelectListComponent(codec, configuration.getBlockedPackets(), false, this::update);

        JPanel extraFilters = new JPanel();
        extraFilters.setLayout(new BoxLayout(extraFilters, BoxLayout.Y_AXIS));
        extraFilters.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        blockResourcePacks = new JCheckBox("Block resource packs?", false);
        blockResourcePacks.setAlignmentX(Component.LEFT_ALIGNMENT);
        blockResourcePacks.addActionListener(e -> update());
        extraFilters.add(blockResourcePacks);

        extraFilters.add(Box.createVerticalGlue());

        this.add(packetSelectListComponent, BorderLayout.CENTER);
        this.add(extraFilters, BorderLayout.EAST);

        this.update();
    }

    private void update() {
        Set<Class<? extends BedrockPacket>> selectedPackets = packetSelectListComponent.getSelectedPackets();

        updateHandler.accept(blockResourcePacks.isSelected(), selectedPackets.stream().map(Class::getSimpleName).collect(Collectors.toUnmodifiableSet()));
    }
}
