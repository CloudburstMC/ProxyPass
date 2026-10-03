package org.cloudburstmc.proxypass.ui.components.swing;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class JLabelledNumberSpinner extends JPanel {
    private final SpinnerNumberModel model;

    public JLabelledNumberSpinner(String label, int min, int max, int def, int step) {
        model = new SpinnerNumberModel(def, min, max, step);

        this.setLayout(new GridLayout(1, 2, 2, 2));

        this.add(new JLabel(label));
        this.add(new JSpinner(model));
    }

    public int getValue() {
        return (int) model.getValue();
    }

    public void addChangeListener(ChangeListener l) {
        model.addChangeListener(l);
    }

    public SpinnerNumberModel getModel() {
        return model;
    }
}
