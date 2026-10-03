package org.cloudburstmc.proxypass.ui;

import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import org.cloudburstmc.proxypass.Configuration;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.auth.Account;
import org.cloudburstmc.proxypass.auth.AuthHandler;
import org.cloudburstmc.proxypass.network.bedrock.util.LogTo;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.ui.components.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.io.InputStream;
import java.util.Objects;

public class PacketLoggingWindow extends JFrame {
    private static final Logger log = LoggerFactory.getLogger(PacketLoggingWindow.class);

    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ByteBuf.class, (JsonSerializer<ByteBuf>) (src, typeOfSrc, context) -> {
                int readerIndex = src.readerIndex();
                byte[] bytes = new byte[src.readableBytes()];
                src.readBytes(bytes);
                src.readerIndex(readerIndex);

                JsonArray array = new JsonArray();
                for (byte b : bytes) {
                    array.add(b & 0xFF);
                }
                return array;
            })
            .registerTypeAdapter(Color.class, (JsonSerializer<Color>) (src, typeOfSrc, context) -> {
                String red = Integer.toString(src.getRed(), 16);
                if (red.length() == 1) red = "0" + red;
                String green = Integer.toString(src.getGreen(), 16);
                if (green.length() == 1) green = "0" + green;
                String blue = Integer.toString(src.getBlue(), 16);
                if (blue.length() == 1) blue = "0" + blue;

                String hex = "#" + red + green + blue;

                if (src.getAlpha() < 255) {
                    String alpha = Integer.toString(src.getAlpha(), 16);
                    if (alpha.length() == 1) alpha = "0" + alpha;
                    hex = hex + alpha;
                }

                return new JsonPrimitive(hex);
            })
            .setPrettyPrinting()
            .create();
    private final Configuration configuration;
    private volatile ProxyPass proxyPass;
    private Consumer<ProxyPlayerSession> sessionInitHandler;

    public PacketLoggingWindow() throws IOException {
        this.setTitle("ProxyPass");
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        this.setLayout(new BorderLayout());

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (proxyPass == null) {
                    System.exit(0);
                } else {
                    proxyPass.closeAsync().whenComplete((ignored, failure) -> System.exit(0));
                }
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                PacketLoggingWindow.this.revalidate();
            }
        });

        log.info("Loading configuration...");
        Path configPath = Paths.get(".").resolve("config.yml");
        if (Files.notExists(configPath) || !Files.isRegularFile(configPath)) {
            try (InputStream input = Objects.requireNonNull(ProxyPass.class.getClassLoader().getResourceAsStream("config.yml"), "config.yml")) {
                Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        configuration = Configuration.load(configPath);

        ConnectionHandler connectionHandler = new ConnectionHandler(configuration, (handler, proxyAddress, targetAddress, account) -> {
            if (proxyPass != null && proxyPass.getRunning().get()) {
                proxyPass.closeAsync().thenRun(() -> SwingUtilities.invokeLater(handler::onStop));
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    updateConfig(Configuration.Address.from(proxyAddress), Configuration.Address.from(targetAddress),
                            null, null, null, null, null, null, null);
                    Account acc = AuthHandler.fromObject(account);
                    if (acc != null) {
                        acc.refresh();
                    }
                    ProxyPass next = new ProxyPass();
                    next.setSessionInitHandler(this.sessionInitHandler);
                    this.proxyPass = next;
                    try {
                        next.start(configuration, acc);
                    } catch (IOException | RuntimeException failure) {
                        next.close();
                        throw failure;
                    }
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
                if (failure == null) {
                    handler.onStart();
                } else {
                    handler.onStop();
                    log.error("Unable to start the proxy", failure);
                    JOptionPane.showMessageDialog(this, failure.getCause().getMessage(),
                            "Unable to start proxy", JOptionPane.ERROR_MESSAGE);
                }
            }));
        });

        this.add(connectionHandler, BorderLayout.NORTH);

        JTabbedPane pane = new JTabbedPane();

        pane.addTab("Log", new LogComponent());

        this.add(pane, BorderLayout.CENTER);

        this.sessionInitHandler = session -> SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());

            PacketViewerComponent packetViewer = new PacketViewerComponent();

            JScrollPane scrollablePacketList = new JScrollPane();
            scrollablePacketList.setAutoscrolls(true);
            PacketListComponent packetList = new PacketListComponent(packetViewer::setCurrentPacket);
            scrollablePacketList.setViewportView(packetList);

            session.setExtraLogHandler((wrapper, direction) -> {
                int readerIndex = wrapper.getPacketBuffer().readerIndex();
                byte[] bytes = new byte[wrapper.getPacketBuffer().readableBytes()];
                wrapper.getPacketBuffer().readBytes(bytes).readerIndex(readerIndex);
                String json;
                try {
                    json = ProxyPass.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper.getPacket());
                } catch (JacksonException e) {
                    log.error("Unable to JSONify %s!".formatted(wrapper.getPacket().getClass().getSimpleName()), e);
                    json = "Failed to JSONify! See console output for more details.";
                }

                BedrockPacket packet = wrapper.getPacket();
                int packetId = wrapper.getPacketId();
                Instant timestamp = Instant.now();
                String packetJson = json;
                SwingUtilities.invokeLater(() -> packetList.newPacket(new UIPacketData(
                        UIPacketData.freeIndex++, packet, packetId, timestamp, direction, bytes, packetJson)));

            });

            SouthBarComponent southBar = new SouthBarComponent(proxyPass, ProxyPass.CODEC, configuration, packetList::addFilter, (packsEnabled, blockPackets) -> {
                this.updateConfig(
                        null, null, null, null,
                        null, null, packsEnabled, null, blockPackets
                );
            });

            JPanel center = new JPanel();
            center.setLayout(new GridLayout(1, 0, 5, 5));
            center.add(scrollablePacketList);
            center.add(packetViewer);
            panel.add(center, BorderLayout.CENTER);

            panel.add(southBar, BorderLayout.SOUTH);

            int index = pane.getTabCount();

            String titleName = "%s (%s)".formatted(session.getAuthData().getDisplayName(), session.getAuthData().getXuid());

            pane.addTab(titleName, panel);

            session.setOnClose(() -> SwingUtilities.invokeLater(() -> {
                pane.setTitleAt(index, titleName + " (Disconnected)");
                pane.setBackgroundAt(index, new Color(255, 0, 0, 64));
            }));
        });

        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }

    public void updateConfig(
            Configuration.Address proxy,
            Configuration.Address destination,
            Boolean packetTesting,
            Boolean loggingPackets,
            Integer maxClients,
            LogTo logTo,
            Boolean ignoreResourcePacks,
            Set<String> ignoredPackets,
            Set<String> blockedPackets
    ) {
        if (proxy != null) configuration.setProxy(proxy);
        if (destination != null) configuration.setDestination(destination);
        if (packetTesting != null) configuration.setPacketTesting(packetTesting);
        if (loggingPackets != null) configuration.setLoggingPackets(loggingPackets);
        if (maxClients != null) configuration.setMaxClients(maxClients);
        if (logTo != null) configuration.setLogTo(logTo);
        if (ignoreResourcePacks != null) configuration.setIgnoreResourcePacks(ignoreResourcePacks);
        if (ignoredPackets != null) configuration.setIgnoredPackets(ignoredPackets);
        if (blockedPackets != null) configuration.setBlockedPackets(blockedPackets);

        if (proxyPass != null && proxyPass.getRunning().get()) {
            proxyPass.resetConfig(configuration);
        }
    }
}
