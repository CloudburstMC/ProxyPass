package com.nukkitx.proxypass.network.bedrock.session;

import com.nukkitx.protocol.PlayerSession;
import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.handler.TailHandler;
import com.nukkitx.protocol.bedrock.handler.WrapperTailHandler;
import com.nukkitx.protocol.bedrock.session.BedrockSession;
import com.nukkitx.protocol.bedrock.wrapper.WrappedPacket;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.util.EncryptionUtils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
@Getter
public class ProxyPlayerSession implements PlayerSession {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final BedrockSession<ProxyPlayerSession> upstream;
    private final BedrockSession<ProxyPlayerSession> downstream;
    private final Path dataPath;
    private final Path logPath;
    private final long timestamp = System.currentTimeMillis();
    private final ProxyPass proxy;
    @Getter(AccessLevel.PACKAGE)
    private final KeyPair proxyKeyPair = EncryptionUtils.createKeyPair();
    private final Deque<String> logBuffer = new ArrayDeque<>();

    public ProxyPlayerSession(BedrockSession<ProxyPlayerSession> upstream, BedrockSession<ProxyPlayerSession> downstream, ProxyPass proxy) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.proxy = proxy;
        this.dataPath = proxy.getSessionsDir().resolve(upstream.getAuthData().getDisplayName() + '-' + timestamp);
        this.logPath = dataPath.resolve("packets.log");
        try {
            Files.createDirectories(dataPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (proxy.getConfiguration().isLoggingPackets()) {
            executor.scheduleAtFixedRate(this::flushLogBuffer, 5, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void onDisconnect(@Nullable String s) {
    }

    @Override
    public void onTimeout() {

    }

    public TailHandler getUpstreamTailHandler() {
        return new UpstreamTailHandler();
    }

    public TailHandler getDownstreamTailHandler() {
        return new DownstreamTailHandler();
    }

    public WrapperTailHandler<ProxyPlayerSession> getUpstreamWrapperTailHandler() {
        return new ProxyWrapperTailHandler(downstream);
    }

    public WrapperTailHandler<ProxyPlayerSession> getDownstreamWrapperTailHandler() {
        return new ProxyWrapperTailHandler(upstream);
    }

    private void log(String toLog) {
        if (proxy.getConfiguration().isLoggingPackets()) {
            synchronized (logBuffer) {
                logBuffer.addLast(toLog);
            }
        }
    }

    private void flushLogBuffer() {
        synchronized (logBuffer) {
            try {
                Files.write(logPath, logBuffer, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                logBuffer.clear();
            } catch (IOException e) {
                log.error("Unable to flush packet log", e);
            }
        }
    }

    @RequiredArgsConstructor
    private class UpstreamTailHandler implements TailHandler {

        @Override
        public boolean handle(BedrockPacket packet, boolean packetsHandled) {
            if (!proxy.getConfiguration().isPassingThrough() || packetsHandled) {
                downstream.sendPacketImmediately(packet);
            }
            if (log.isTraceEnabled()) {
                log.trace("Outbound {}: {}", downstream.getRemoteAddress().orElse(null), packet);
            }
            log("[SERVER BOUND]  -  " + packet.toString());
            return packetsHandled;
        }
    }

    @RequiredArgsConstructor
    private class DownstreamTailHandler implements TailHandler {

        @Override
        public boolean handle(BedrockPacket packet, boolean packetsHandled) {
            if (!proxy.getConfiguration().isPassingThrough() || packetsHandled) {
                upstream.sendPacketImmediately(packet);
            }
            log("[CLIENT BOUND]  -  " + packet.toString());
            return packetsHandled;
        }
    }

    @RequiredArgsConstructor
    private class ProxyWrapperTailHandler implements WrapperTailHandler<ProxyPlayerSession> {
        private final BedrockSession<ProxyPlayerSession> session;

        @Override
        public void handle(WrappedPacket<ProxyPlayerSession> packet, boolean packetsHandled) {
            if (!packetsHandled && proxy.getConfiguration().isPassingThrough()) {
                packet.getBatched().retain();
                packet.getBatched().readerIndex(0);
                packet.getBatched().writerIndex(packet.getBatched().readableBytes());
                session.sendWrapped(packet);
            }
        }
    }
}
