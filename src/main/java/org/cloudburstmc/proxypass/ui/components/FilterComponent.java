package org.cloudburstmc.proxypass.ui.components;

import lombok.extern.slf4j.Slf4j;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.Configuration;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.ui.UIPacketData;
import org.cloudburstmc.proxypass.ui.components.swing.JLabelledNumberSpinner;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Slf4j
public class FilterComponent extends JPanel {
    private final ProxyPass proxyPass;
    private final Consumer<Predicate<UIPacketData>> predicateConsumer;
    private final PacketSelectListComponent packetSelectListComponent;
    private final JCheckBox whitelistMode;
    private final JLabelledNumberSpinner minByteCount;
    private final JLabelledNumberSpinner maxByteCount;
    private final JCheckBox s2cEnabled;
    private final JCheckBox c2sEnabled;

    public FilterComponent(ProxyPass proxyPass, BedrockCodec codec, Configuration configuration, Consumer<Predicate<UIPacketData>> predicateConsumer) {
        this.setLayout(new BorderLayout());
        this.proxyPass = proxyPass;
        this.predicateConsumer = predicateConsumer;

        packetSelectListComponent = new PacketSelectListComponent(codec, configuration.getIgnoredPackets(), false, this::updatePredicate);

        JPanel extraFilters = new JPanel();
        extraFilters.setLayout(new BoxLayout(extraFilters, BoxLayout.Y_AXIS));
        extraFilters.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        whitelistMode = new JCheckBox("Is whitelist?", false);
        whitelistMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        whitelistMode.addActionListener(e -> updatePredicate());
        extraFilters.add(whitelistMode);

        s2cEnabled = new JCheckBox("Show Clientbound Packets", true);
        s2cEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        s2cEnabled.addActionListener(e -> updatePredicate());
        extraFilters.add(s2cEnabled);

        c2sEnabled = new JCheckBox("Show Serverbound Packets", true);
        c2sEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        c2sEnabled.addActionListener(e -> updatePredicate());
        extraFilters.add(c2sEnabled);

        minByteCount = new JLabelledNumberSpinner("Minimum Byte Count: ", 0, Integer.MAX_VALUE, 0, 1);
        minByteCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        extraFilters.add(minByteCount);

        maxByteCount = new JLabelledNumberSpinner("Maximum Byte Count: ", 0, Integer.MAX_VALUE, 500000, 1);
        maxByteCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        extraFilters.add(maxByteCount);

        maxByteCount.addChangeListener(e -> {
            SpinnerNumberModel model = minByteCount.getModel();
            model.setMaximum(maxByteCount.getValue());
            updatePredicate();
        });

        minByteCount.addChangeListener(e -> {
            SpinnerNumberModel model = maxByteCount.getModel();
            model.setMinimum(minByteCount.getValue());
            updatePredicate();
        });

        extraFilters.add(Box.createVerticalGlue());

        this.add(packetSelectListComponent, BorderLayout.CENTER);
        this.add(extraFilters, BorderLayout.EAST);

        updatePredicate();
    }

    public void updatePredicate() {
        Set<Class<? extends BedrockPacket>> selectedPackets = packetSelectListComponent.getSelectedPackets();
        int minBytes = (int) minByteCount.getModel().getValue();
        int maxBytes = (int) maxByteCount.getModel().getValue();
        boolean s2cValid = s2cEnabled.isSelected();
        boolean c2sValid = c2sEnabled.isSelected();

        predicateConsumer.accept(packet -> {
            if (whitelistMode.isSelected()) {
                if (!selectedPackets.contains(packet.packet().getClass())) return false;
            } else {
                if (selectedPackets.contains(packet.packet().getClass())) return false;
            }

            if (packet.rawBytes().length > maxBytes || packet.rawBytes().length < minBytes) return false;

            if (!s2cValid && packet.direction().equals(UIPacketData.Direction.S2C)) return false;
            return c2sValid || !packet.direction().equals(UIPacketData.Direction.C2S);
        });
    }
}
