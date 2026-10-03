package org.cloudburstmc.proxypass.auth;

import com.google.gson.*;
import lombok.SneakyThrows;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cloudburstmc.proxypass.Configuration;

import java.awt.*;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.function.Consumer;

import static org.cloudburstmc.proxypass.ProxyPass.CODEC;

public class AuthHandler {
    private static final Logger log = LogManager.getLogger(AuthHandler.class);

    private AuthHandler() {}

    private static final HttpClient CLIENT = MinecraftAuth.createHttpClient();
    public static final BedrockAuthManager.Builder AUTH_MANAGER = BedrockAuthManager.create(CLIENT, CODEC.getMinecraftVersion());
    private static final Gson GSON = new GsonBuilder()
            .create();

    public static Account authenticateCli(Configuration configuration) throws Exception {
        if (!configuration.isOnlineMode()) return null;

        log.info("Authenticating online...");

        Path authPath = Paths.get(".").resolve("auth.json");

        if (Files.notExists(authPath)) {
            Account account = newAuthCli();
            if (configuration.isSaveAuthDetails()) {
                JsonArray array = new JsonArray();
                array.add(account.toJson());
                Files.writeString(authPath, GSON.toJson(array));
            }
            return account;
        }

        JsonArray accounts = GSON.fromJson(new FileReader(authPath.toFile()), JsonArray.class);
        Account account;
        if (configuration.getDefaultAccountName().isEmpty()) {
            account = selectAccountCli(accounts, null);
        } else {
            account = selectAccountCli(accounts, configuration.getDefaultAccountName());
        }
        return account;
    }

    @SneakyThrows
    public static JsonArray readRawAccounts() {
        Path authPath = Paths.get(".").resolve("auth.json");
        if (Files.notExists(authPath)) return new JsonArray();
        return GSON.fromJson(new FileReader(authPath.toFile()), JsonArray.class);
    }

    @SneakyThrows
    public static void writeRawAccounts(JsonArray array) {
        Path authPath = Paths.get(".").resolve("auth.json");
        Files.writeString(authPath, GSON.toJson(array));
    }

    public static void addAccount(JsonObject newAccount) {
        JsonArray rawAccounts = readRawAccounts();
        JsonArray newAccounts = new JsonArray();

        for (JsonElement account : rawAccounts) {
            if (getXuid(account.getAsJsonObject()).equals(getXuid(newAccount))) continue;

            newAccounts.add(account);
        }

        newAccounts.add(newAccount);
        writeRawAccounts(newAccounts);
    }

    private static Account newAuthCli() throws Exception {
        BedrockAuthManager authManager = AUTH_MANAGER.login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) msaDeviceCode -> {
            URI verificationUri = URI.create(msaDeviceCode.getDirectVerificationUri());

            log.info("Go to {}", verificationUri);
            log.info("Enter code {}", msaDeviceCode.getUserCode());

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(verificationUri);
                } catch (IOException e) {
                    log.error("Failed to open browser", e);
                }
            }
        });
        Account account = new Account(authManager);
        account.refresh();
        return account;
    }

    private static Account selectAccountCli(JsonArray accounts, String defaultAccountName) throws Exception {
        JsonObject fallbackObj = accounts.get(0).getAsJsonObject();
        Account fallback = new Account(fallbackObj, CLIENT, CODEC.getMinecraftVersion());

        if (defaultAccountName == null) return fallback;

        for (JsonElement account : accounts) {
            if (getAccountName(account.getAsJsonObject()).equals(defaultAccountName)) return new Account(account.getAsJsonObject(), CLIENT, CODEC.getMinecraftVersion());
        }

        return fallback;
    }

    @SneakyThrows
    public static Account fromObject(JsonObject object) {
        if (object == null) return null;
        return new Account(object, CLIENT, CODEC.getMinecraftVersion());
    }

    public static String getAccountName(JsonObject object) {
        try {
            JsonObject minecraftCertificateChain = object.getAsJsonObject("minecraftCertificateChain");
            String encodedIdentityJwt = minecraftCertificateChain.get("identityJwt").getAsString().split("\\.")[1];
            JsonObject identityJwt = GSON.fromJson(new String(Base64.getDecoder().decode(encodedIdentityJwt)), JsonObject.class);
            JsonObject extraData = identityJwt.getAsJsonObject("extraData");
            String displayName = extraData.get("displayName").getAsString();
            String xuid = extraData.get("XUID").getAsString();
            return "%s (%s)".formatted(displayName, xuid);
        } catch (NullPointerException e) {
            return null;
        }
    }

    public static String getDisplayName(JsonObject object) {
        try {
            JsonObject minecraftCertificateChain = object.getAsJsonObject("minecraftCertificateChain");
            String encodedIdentityJwt = minecraftCertificateChain.get("identityJwt").getAsString().split("\\.")[1];
            JsonObject identityJwt = GSON.fromJson(new String(Base64.getDecoder().decode(encodedIdentityJwt)), JsonObject.class);
            JsonObject extraData = identityJwt.getAsJsonObject("extraData");
            return extraData.get("displayName").getAsString();
        } catch (NullPointerException e) {
            return null;
        }
    }

    public static String getXuid(JsonObject object) {
        try {
            JsonObject minecraftCertificateChain = object.getAsJsonObject("minecraftCertificateChain");
            String encodedIdentityJwt = minecraftCertificateChain.get("identityJwt").getAsString().split("\\.")[1];
            JsonObject identityJwt = GSON.fromJson(new String(Base64.getDecoder().decode(encodedIdentityJwt)), JsonObject.class);
            JsonObject extraData = identityJwt.getAsJsonObject("extraData");
            return extraData.get("XUID").getAsString();
        } catch (NullPointerException e) {
            return null;
        }
    }
}
