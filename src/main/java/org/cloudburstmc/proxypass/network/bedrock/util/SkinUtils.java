package org.cloudburstmc.proxypass.network.bedrock.util;

import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyPlayerSession;
import org.jose4j.json.internal.json_simple.JSONObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

public class SkinUtils {

    private static final int PIXEL_SIZE = 4;

    public static final int SINGLE_SKIN_SIZE = 64 * 32 * PIXEL_SIZE;
    public static final int DOUBLE_SKIN_SIZE = 64 * 64 * PIXEL_SIZE;
    public static final int SKIN_128_64_SIZE = 128 * 64 * PIXEL_SIZE;
    public static final int SKIN_128_128_SIZE = 128 * 128 * PIXEL_SIZE;
    public static final int SKIN_256_256_SIZE = 256 * 256 * PIXEL_SIZE;

    public static void saveSkin(ProxyPlayerSession session, JSONObject skinData) {
        byte[] skin = Base64.getDecoder().decode(JsonUtils.childAsType(skinData, "SkinData", String.class));
        int width, height;
        if (skin.length == SINGLE_SKIN_SIZE) {
            width = 64;
            height = 32;
        } else if (skin.length == DOUBLE_SKIN_SIZE) {
            width = 64;
            height = 64;
        } else if (skin.length == SKIN_128_64_SIZE) {
            width = 128;
            height = 64;
        } else if (skin.length == SKIN_128_128_SIZE) {
            width = 128;
            height = 128;
        } else if (skin.length == SKIN_256_256_SIZE) {
            width = 256;
            height = 256;
        } else {
            throw new IllegalStateException("Invalid skin");
        }
        saveImage(session, width, height, skin, "skin");

        byte[] cape = Base64.getDecoder().decode(JsonUtils.childAsType(skinData, "CapeData", String.class));
        if (cape.length != 0) {
            saveImage(session, 64, 32, cape, "cape");
        }

        byte[] geometry = Base64.getDecoder().decode(JsonUtils.childAsType(skinData, "SkinGeometryData", String.class));
        session.getLogger().saveJson("geometryData", geometry);
    }

    private static void saveImage(ProxyPlayerSession session, int width, int height, byte[] bytes, String name) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);

        ByteArrayInputStream data = new ByteArrayInputStream(bytes);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(data.read(), data.read(), data.read(), data.read());
                image.setRGB(x, y, color.getRGB());
            }
        }

        session.getLogger().saveImage(name, image);
    }

}
