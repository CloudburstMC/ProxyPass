package com.nukkitx.proxypass.network.bedrock.logging;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockSession;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


@Log4j2
public class PacketLogger {

    public static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final ProxyPass proxy;

    private final Path dataPath;

    private final Path logPath;

    private final Deque<String> logBuffer = new ArrayDeque<>();

    public PacketLogger(ProxyPass proxy, Path sessionsDir, String displayName, long timestamp) {
        this.proxy = proxy;
        this.dataPath = sessionsDir.resolve(displayName + '-' + timestamp);
        this.logPath = dataPath.resolve("packets.log");

        if (proxy.getConfiguration().isLoggingPackets() && proxy.getConfiguration().getLogTo().logToFile) {
            log.debug("Packets will be logged under " + getLogPath().toString());
            try {
                Files.createDirectories(getDataPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Path getDataPath() {
        return dataPath;
    }

    public Path getLogPath() {
        return logPath;
    }

    public Deque<String> getLogBuffer() {
        return logBuffer;
    }

    public void log(Supplier<String> supplier) {
        if (proxy.getConfiguration().isLoggingPackets()) {
            synchronized (getLogBuffer()) {
                getLogBuffer().addLast(supplier.get());
            }
        }
    }

    private void flushLogBuffer() {
        synchronized (getLogBuffer()) {
            try {
                if (proxy.getConfiguration().getLogTo().logToFile) {
                    Files.write(getLogPath(), getLogBuffer(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                }
                getLogBuffer().clear();
            } catch (IOException e) {
                log.error("Unable to flush packet log", e);
            }
        }
    }

    public void start(ProxyPass proxy) {
        if (proxy.getConfiguration().isLoggingPackets()) {
            executor.scheduleAtFixedRate(this::flushLogBuffer, 5, 5, TimeUnit.SECONDS);
        }
    }

    public void logPacket(BedrockSession session, BedrockPacket packet, boolean upstream) {
        String logPrefix = getLogPrefix(upstream);
        if (!proxy.isIgnoredPacket(packet.getClass())) {
            if (session.isLogging() && log.isTraceEnabled()) {
                log.trace(logPrefix + " {}: {}", session.getAddress(), packet);
            }
            log(() -> logPrefix + packet.toString());
            if (proxy.getConfiguration().isLoggingPackets() && proxy.getConfiguration().getLogTo().logToConsole) {
                System.out.println(logPrefix + packet.toString());
            }
        }
    }

    private String getLogPrefix(boolean upstream) {
        return upstream ? "[SERVER BOUND]  -  " : "[CLIENT BOUND]  -  ";
    }

}
