package com.nukkitx.proxypass.network.bedrock.session;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.nbt.stream.LittleEndianDataOutputStream;
import com.nukkitx.nbt.stream.NBTOutputStream;
import com.nukkitx.nbt.tag.CompoundTag;
import com.nukkitx.protocol.bedrock.data.ContainerId;
import com.nukkitx.protocol.bedrock.data.ItemData;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.session.BedrockSession;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.util.EncryptionUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
public class DownstreamPacketHandler implements BedrockPacketHandler {
    private final BedrockSession<ProxyPlayerSession> session;
    private final ProxyPass proxy;

    public boolean handle(ServerToClientHandshakePacket packet) {
        try {
            SignedJWT saltJwt = SignedJWT.parse(packet.getJwt());
            URI x5u = saltJwt.getHeader().getX509CertURL();
            ECPublicKey serverKey = EncryptionUtils.generateKey(x5u.toASCIIString());
            SecretKey key = EncryptionUtils.getServerKey(session.getPlayer().getProxyKeyPair(), serverKey,
                    Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt")));
            session.enableEncryption(key);
        } catch (ParseException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
        session.sendPacketImmediately(clientToServerHandshake);
        return true;
    }

    public boolean handle(AvailableEntityIdentifiersPacket packet) {
        proxy.saveData("entity_identifiers", packet.getTag());
        return false;
    }

    public boolean handle(BiomeDefinitionListPacket packet) {
        proxy.saveData("biome_definitions", packet.getTag());
        return false;
    }

    public boolean handle(StartGamePacket packet) {
        Path legacyIdMapPath = proxy.getDataDir().resolve("legacy_block_ids.json");
        if (Files.notExists(legacyIdMapPath) || !Files.isRegularFile(legacyIdMapPath)) {
            return false;
        }

        Map<String, Integer> map;
        try {
            map = ProxyPass.JSON_MAPPER.readValue(Files.newInputStream(legacyIdMapPath), new TypeReference<Map<String, Integer>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException();
        }

        List<RuntimeEntry> table = new ArrayList<>();
        for (StartGamePacket.PaletteEntry entry : packet.getPaletteEntries()) {
            String name = entry.getIdentifier();
            table.add(new RuntimeEntry(name, map.get(name), entry.getMeta()));
        }
        Collections.sort(table);

        proxy.saveObject("runtimeid_table.json", table);

        return false;
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        return packet.getKickMessage().equals("disconnectionScreen.notAuthenticated");
    }

    @Override
    public boolean handle(InventoryContentPacket packet) {
        if (packet.getContainerId() == ContainerId.CREATIVE) {
            List<CreativeItemEntry> entries = new ArrayList<>();
            for (ItemData data : packet.getContents()) {
                int id = data.getId();
                Integer damage = data.getDamage() == 0 ? null : (int) data.getDamage();

                CompoundTag tag = data.getTag();
                String tagData = null;
                if (tag != null) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    try (NBTOutputStream stream = new NBTOutputStream(new LittleEndianDataOutputStream(byteArrayOutputStream))) {
                        stream.write(tag);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    tagData = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
                }
                entries.add(new CreativeItemEntry(id, damage, tagData));
            }

            proxy.saveObject("creative_items.json", entries);
        }
        return false;
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CreativeItemEntry {
        private final int id;
        private final Integer damage;
        @JsonProperty("nbt_b64")
        private final String nbt;
    }

    @Value
    private static class RuntimeEntry implements Comparable<RuntimeEntry> {
        private final String name;
        private final int id;
        private final int data;

        @Override
        public int compareTo(@Nonnull RuntimeEntry that) {
            int comparison = Integer.compare(this.id, that.id);
            if (comparison == 0) {
                comparison = Integer.compare(this.data, that.data);
            }
            return comparison;
        }
    }
}
