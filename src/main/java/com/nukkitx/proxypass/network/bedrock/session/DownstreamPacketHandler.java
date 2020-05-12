package com.nukkitx.proxypass.network.bedrock.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.nbt.stream.LittleEndianDataOutputStream;
import com.nukkitx.nbt.stream.NBTOutputStream;
import com.nukkitx.nbt.tag.CompoundTag;
import com.nukkitx.nbt.tag.ListTag;
import com.nukkitx.protocol.bedrock.BedrockClientSession;
import com.nukkitx.protocol.bedrock.data.ContainerId;
import com.nukkitx.protocol.bedrock.data.ItemData;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.*;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.util.BlockPaletteUtils;
import com.nukkitx.proxypass.network.bedrock.util.ForgeryUtils;
import com.nukkitx.proxypass.network.bedrock.util.RecipeUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
public class DownstreamPacketHandler implements BedrockPacketHandler {
    private final BedrockClientSession session;
    private final ProxyPlayerSession player;
    private final ProxyPass proxy;

    public boolean handle(ServerToClientHandshakePacket packet) {
        try {
            SignedJWT saltJwt = SignedJWT.parse(packet.getJwt());
            URI x5u = saltJwt.getHeader().getX509CertURL();
            ECPublicKey serverKey = EncryptionUtils.generateKey(x5u.toASCIIString());
            SecretKey key = EncryptionUtils.getSecretKey(this.player.getProxyKeyPair().getPrivate(), serverKey,
                    Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt")));
            session.enableEncryption(key);

            ServerToClientHandshakePacket p = new ServerToClientHandshakePacket();
            p.setJwt(ForgeryUtils.forgeHandshake(
                    player.getProxyKeyPair(),
                    saltJwt.getJWTClaimsSet().getStringClaim("signedToken"),
                    Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt"))).serialize()
            );
            player.getUpstream().sendPacketImmediately(p);
            player.getUpstream().enableEncryption(EncryptionUtils.getSecretKey(player.getProxyKeyPair().getPrivate(),
                    ((UpstreamPacketHandler)player.getUpstream().getPacketHandler()).getRemotePublicKey(),Base64.getDecoder().decode(saltJwt.getJWTClaimsSet().getStringClaim("salt"))));

        } catch (ParseException | NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }

        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
        session.sendPacketImmediately(clientToServerHandshake);
        return true;
    }

    public boolean handle(AvailableEntityIdentifiersPacket packet) {
        proxy.saveNBT("entity_identifiers", packet.getTag());
        return false;
    }

    public boolean handle(BiomeDefinitionListPacket packet) {
        proxy.saveNBT("biome_definitions", packet.getTag());
        return false;
    }

    public boolean handle(StartGamePacket packet) {
        Map<String, Integer> legacyBlocks = new HashMap<>();
        for (CompoundTag entry : packet.getBlockPalette().getValue()) {
            legacyBlocks.putIfAbsent(entry.getCompound("block").getString("name"), (int) entry.getShort("id"));
        }

        proxy.saveJson("legacy_block_ids.json", sortMap(legacyBlocks));
        List<CompoundTag> palette = new ArrayList<>(packet.getBlockPalette().getValue());
        palette.sort(Comparator.comparingInt(value -> value.getShort("id")));
        proxy.saveNBT("runtime_block_states", new ListTag<>("", CompoundTag.class, palette));
        BlockPaletteUtils.convertToJson(proxy, palette);

        List<DataEntry> itemData = new ArrayList<>();
        LinkedHashMap<String, Integer> legacyItems = new LinkedHashMap<>();

        for (StartGamePacket.ItemEntry entry : packet.getItemEntries()) {
            itemData.add(new DataEntry(entry.getIdentifier(), entry.getId()));
            if (entry.getId() > 255) {
                legacyItems.putIfAbsent(entry.getIdentifier(), (int) entry.getId());
            }
        }

        proxy.saveJson("legacy_item_ids.json", sortMap(legacyItems));
        proxy.saveJson("runtime_item_states.json", itemData);

        return false;
    }

    @Override
    public boolean handle(CraftingDataPacket packet) {
        RecipeUtils.writeRecipes(packet, this.proxy);

        return false;
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        this.session.disconnect();
        // Let the client see the reason too.
        return false;
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

            CreativeItems items = new CreativeItems(entries);

            proxy.saveJson("creative_items.json", items);
        }
        return false;
    }

    private static Map<String, Integer> sortMap(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Map.Entry.comparingByValue());

        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
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
    private static class CreativeItems {
        private final List<CreativeItemEntry> items;
    }

    @Value
    private static class RuntimeEntry {
        private static final Comparator<RuntimeEntry> COMPARATOR = Comparator.comparingInt(RuntimeEntry::getId)
                .thenComparingInt(RuntimeEntry::getData);

        private final String name;
        private final int id;
        private final int data;
    }

    @Value
    private static class DataEntry {
        private final String name;
        private final int id;
    }
}
