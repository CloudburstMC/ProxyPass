package org.cloudburstmc.proxypass.ui.components.swing;

import javax.swing.*;
import java.awt.*;
import java.net.InetSocketAddress;

public class JAddressField extends JPanel {
    private final JHintTextField address;
    private final JSpinner port;

    public JAddressField(String label, String defaultIp, int defaultPort) {
        this.setLayout(new BorderLayout(2, 2));

        this.add(new JLabel(label), BorderLayout.WEST);
        this.address = new JHintTextField(defaultIp);
        this.add(this.address, BorderLayout.CENTER);
        this.port = new JSpinner(new SpinnerNumberModel(defaultPort, 0, 65535, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(this.port, "#0");
        this.port.setEditor(editor);
        this.add(this.port, BorderLayout.EAST);
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(address.getTextOrHint(), (int) port.getValue());
    }
}
