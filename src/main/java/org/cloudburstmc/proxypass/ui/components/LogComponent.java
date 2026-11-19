package org.cloudburstmc.proxypass.ui.components;

import lombok.SneakyThrows;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.LogManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogComponent extends JPanel {
    private final List<LogLine> logLines = new ArrayList<>();
    private final JTextArea area;
    private final JComboBox<Level> maxLevel;

    public LogComponent() {
        this.setLayout(new BorderLayout());

        JPanel topbar = new JPanel();
        topbar.setLayout(new BorderLayout());

        this.maxLevel = new JComboBox<>(Arrays.stream(Level.values()).sorted().toArray(Level[]::new));
        maxLevel.setSelectedItem(Level.INFO);
        maxLevel.addItemListener(e -> {
            update();
        });
        topbar.add(maxLevel, BorderLayout.CENTER);

        JButton clear = new JButton("Clear Logs");
        clear.addActionListener(e -> {
            this.logLines.clear();
            update();
        });
        topbar.add(clear, BorderLayout.EAST);

        this.add(topbar, BorderLayout.NORTH);

        this.area = new JTextArea();
        this.area.setEditable(false);
        this.area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        this.attachAppender();
        this.add(new JScrollPane(this.area), BorderLayout.CENTER);
    }

    private void attachAppender() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();

        Layout<String> layout = PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss} %-4level]: %msg%n")
                .withConfiguration(config)
                .build();

        Appender appender = new AbstractAppender("UILog-" + this.hashCode(), null, layout, false) {
            @Override
            public void append(LogEvent event) {
                Level level = event.getLevel();
                String message = new String(getLayout().toByteArray(event));
                SwingUtilities.invokeLater(() -> {
                    LogComponent.this.logLines.add(new LogLine(level, message));
                    if (level.isMoreSpecificThan((Level) maxLevel.getSelectedItem())) {
                        LogComponent.this.area.append(message);
                        LogComponent.this.area.setCaretPosition(LogComponent.this.area.getDocument().getLength());
                    }
                });
            }
        };
        appender.start();

        config.addAppender(appender);
        config.getRootLogger().addAppender(appender, null, null);
        context.updateLoggers();
    }

    @SneakyThrows
    private void update() {
        this.area.getDocument().remove(0, this.area.getDocument().getLength());
        for (LogLine line : logLines) {
            if (line.level().isMoreSpecificThan((Level) maxLevel.getSelectedItem())) {
                LogComponent.this.area.append(line.message());
                LogComponent.this.area.setCaretPosition(LogComponent.this.area.getDocument().getLength());
            }
        }
    }

    private record LogLine(Level level, String message) {}
}
