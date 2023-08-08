package org.cloudburstmc.proxypass.network.bedrock.session;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import net.raphimc.mcauth.MinecraftAuth;
import net.raphimc.mcauth.step.bedrock.StepMCChain;
import net.raphimc.mcauth.step.bedrock.StepPlayFabToken;
import org.apache.http.impl.client.CloseableHttpClient;

@Log4j2
@Accessors(fluent = true)
@Data
@AllArgsConstructor
// adapted from https://github.com/ViaVersion/ViaProxy/blob/ca40e290092d99abd842f8cce645d8db407de105/src/main/java/net/raphimc/viaproxy/saves/impl/accounts/BedrockAccount.java#L29-L101
public class Account {
    private StepMCChain.MCChain mcChain;
    private StepPlayFabToken.PlayFabToken playFabToken;

    public Account(JsonObject jsonObject) throws Exception {
        this.mcChain = MinecraftAuth.BEDROCK_DEVICE_CODE_LOGIN.fromJson(jsonObject.getAsJsonObject("mc_chain"));
        if (jsonObject.has("play_fab_token")) {
            try {
                this.playFabToken = MinecraftAuth.BEDROCK_PLAY_FAB_TOKEN.fromJson(jsonObject.getAsJsonObject("play_fab_token"));
            } catch (Throwable e) {
                log.warn("Failed to load PlayFab token for Bedrock account. It will be regenerated.", e);
            }
        }
    }

    public JsonObject toJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("mc_chain", mcChain.toJson());
        if (playFabToken != null) {
            jsonObject.add("play_fab_token", playFabToken.toJson());
        }
        return jsonObject;
    }

    public boolean refresh(CloseableHttpClient httpClient) throws Exception {
        mcChain = MinecraftAuth.BEDROCK_DEVICE_CODE_LOGIN.refresh(httpClient, mcChain);
        try {
            if (playFabToken == null) {
                throw new NullPointerException();
            }
            playFabToken = MinecraftAuth.BEDROCK_PLAY_FAB_TOKEN.refresh(httpClient, playFabToken);
        } catch (Throwable e) {
            playFabToken = null;
            playFabToken = MinecraftAuth.BEDROCK_PLAY_FAB_TOKEN.getFromInput(httpClient, mcChain.prevResult().fullXblSession());
        }
        return true;
    }
}
