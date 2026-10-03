package org.cloudburstmc.proxypass.ui.components.swing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Map;

public class JHintTextField extends JTextField {
    private String hint;

    public JHintTextField(String hint) {
        this.hint = hint;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();

            Map<?, ?> desktopHints =
                    (Map<?, ?>) Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");

            g2.setRenderingHints(desktopHints);
            g2.setColor(Color.GRAY);

            FontMetrics fm = g2.getFontMetrics();
            int x = getInsets().left;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

            g2.drawString(hint, x, y);
            g2.dispose();
        }
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
        repaint();
    }

    public String getTextOrHint() {
        if (this.getText().isEmpty()) return this.getHint();
        else return this.getText();
    }
}
