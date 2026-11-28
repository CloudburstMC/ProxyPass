package org.cloudburstmc.proxypass.ui.components.swing;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;

public class JLabelledHintTextField extends JPanel {
    private final JLabel label;
    private final JHintTextField textField;

    public JLabelledHintTextField(String label, String hint) {
        this.label = new JLabel(label);
        this.textField = new JHintTextField(hint);

        this.setLayout(new GridLayout(1, 2, 2, 2));
        this.add(this.label);
        this.add(this.textField);
    }

    public void setText(String text) {
        this.textField.setText(text);
    }

    public String getText() {
        return this.textField.getText();
    }

    public void setHint(String text) {
        this.textField.setHint(text);
    }

    public String getHint() {
        return this.textField.getHint();
    }

    public Document getDocument() {
        return this.textField.getDocument();
    }
}
