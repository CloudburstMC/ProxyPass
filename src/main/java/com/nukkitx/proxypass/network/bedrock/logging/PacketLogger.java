package com.nukkitx.proxypass.network.bedrock.logging;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PacketLogger {

    public static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final Path dataPath;

    private final Path logPath;

    public PacketLogger(Path dataPath, Path logPath) {
        this.dataPath = dataPath;
        this.logPath = logPath;
    }

    public PacketLogger(Path sessionsDir, String displayName, long timestamp) {
        this.dataPath = sessionsDir.resolve(displayName + '-' + timestamp);
        this.logPath = dataPath.resolve("packets.log");
    }

    public Path getDataPath() {
        return dataPath;
    }

    public Path getLogPath() {
        return logPath;
    }

}
