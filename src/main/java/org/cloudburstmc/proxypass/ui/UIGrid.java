package org.cloudburstmc.proxypass.ui;

import java.awt.*;

public record UIGrid(double[] weights, UIRow[] rows) {
    public record UIRow(double[] weights, Component[] components) {
        public UIRow(double weight, Component component) {
            this(new double[]{weight}, new Component[]{component});
        }
    }
}
