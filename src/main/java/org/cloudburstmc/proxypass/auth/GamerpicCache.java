package org.cloudburstmc.proxypass.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;

/**
 * Disk cache for Xbox Live gamerpics, keyed by XUID and icon size, so the app doesn't have to
 * re-authenticate and re-download an account's gamerpic on every launch.
 */
public class GamerpicCache {
    private static final Logger log = LogManager.getLogger(GamerpicCache.class);
    private static final Duration TTL = Duration.ofHours(24);
    private static final Path CACHE_DIR = Paths.get(".").resolve("cache").resolve("gamerpics");
    // Bump this whenever the downscaling algorithm changes so stale cached images (which bake in
    // the resampling quality) are invalidated instead of being served for the rest of their TTL.
    private static final int CACHE_VERSION = 4;

    private GamerpicCache() {
    }

    public static BufferedImage get(String xuid, int size) {
        Path path = pathFor(xuid, size);
        if (Files.notExists(path)) return null;

        try {
            Instant expiresAt = Files.getLastModifiedTime(path).toInstant().plus(TTL);
            if (Instant.now().isAfter(expiresAt)) return null;

            return ImageIO.read(path.toFile());
        } catch (IOException e) {
            log.warn("Failed to read cached gamerpic for {}: {}", xuid, e.toString());
            return null;
        }
    }

    public static void put(String xuid, int size, BufferedImage image) {
        Path path = pathFor(xuid, size);
        try {
            Files.createDirectories(CACHE_DIR);
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException e) {
            log.warn("Failed to cache gamerpic for {}: {}", xuid, e.toString());
        }
    }

    private static Path pathFor(String xuid, int size) {
        return CACHE_DIR.resolve(xuid + "-" + size + "-v" + CACHE_VERSION + ".png");
    }
}
