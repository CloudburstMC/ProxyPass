package org.cloudburstmc.proxypass.auth;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import net.lenni0451.commons.httpclient.HttpClient;
import net.lenni0451.commons.httpclient.HttpResponse;
import net.lenni0451.commons.httpclient.requests.impl.GetRequest;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Slf4j
@Accessors(fluent = true)
@Data
@AllArgsConstructor
// adapted from https://github.com/ViaVersion/ViaProxy/blob/ca40e290092d99abd842f8cce645d8db407de105/src/main/java/net/raphimc/viaproxy/saves/impl/accounts/BedrockAccount.java#L29-L101
public class Account {
    // Xbox's gamerpic CDN only seems to honor a handful of preset widths (64/208/424/1080) -
    // anything else (like a small icon size) is ignored and it falls back to the full ~1080px
    // image, which is why requesting the icon size directly doesn't work and is much slower.
    private static final int GAMERPIC_FETCH_SIZE = 208;

    private BedrockAuthManager authManager;

    public Account(JsonObject jsonObject, HttpClient httpClient, String gameVersion) throws Exception {
        this.authManager = BedrockAuthManager.fromJson(httpClient, gameVersion, jsonObject);
    }

    public JsonObject toJson() {
        return BedrockAuthManager.toJson(this.authManager);
    }

    public boolean refresh() throws Exception {
        authManager.getMinecraftSession().refresh();
        authManager.getMinecraftCertificateChain().refresh();
        authManager.getMinecraftMultiplayerToken().refresh();
        authManager.getRealmsXstsToken().refresh();
        return true;
    }

    /**
     * Fetches the account's Xbox Live gamerpic, downscaled to the given size. This requires a
     * separate XSTS token scoped to xboxlive.com, which is not part of the normal Bedrock auth
     * chain, so it is not cached alongside the other tokens on the auth manager.
     */
    public BufferedImage fetchGamerpic(int size) throws IOException {
        String gamerpicUrl = authManager.getXboxUserProfile().getUpToDate().getSettings().getOrDefault("AppDisplayPicRaw", null);
        HttpResponse imageResponse = authManager.getHttpClient().execute(new GetRequest(gamerpicUrl + "&w=" + GAMERPIC_FETCH_SIZE));
        BufferedImage fullSize = ImageIO.read(new ByteArrayInputStream(imageResponse.getContent().getAsBytes()));
        return makeGamerpic(fullSize, size);
    }

    private static BufferedImage makeGamerpic(BufferedImage source, int targetSize) {
        source = downscale(source, targetSize);

        BufferedImage output = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = output.createGraphics();

        g2.setComposite(AlphaComposite.Src);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        float cornerArc = targetSize;
        g2.fill(new RoundRectangle2D.Float(0, 0, targetSize, targetSize, cornerArc, cornerArc));

        g2.setComposite(AlphaComposite.SrcAtop);
        g2.drawImage(source, 0, 0, null);

        g2.dispose();

        return output;
    }

    private static BufferedImage downscale(BufferedImage source, int targetSize) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage current = source;

        while (width / 2 > targetSize && height / 2 > targetSize) {
            width /= 2;
            height /= 2;
            current = scaleTo(current, width, height, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }

        return scaleTo(current, targetSize, targetSize, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage scaleTo(BufferedImage source, int width, int height, Object interpolationHint) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, width, height, null);
        g2.dispose();
        return scaled;
    }
}
