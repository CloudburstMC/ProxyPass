package com.nukkitx.proxypass.network.bedrock.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nukkitx.nbt.NBTOutputStream;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtType;
import com.nukkitx.nbt.util.stream.LittleEndianDataOutputStream;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.util.RecipeUtils;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.data.defintions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.defintions.SimpleDefinitionRegistry;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Log4j2
@RequiredArgsConstructor
public class DownstreamPacketHandler implements BedrockPacketHandler {
    private final BedrockSession session;
    private final ProxyPlayerSession player;
    private final ProxyPass proxy;

    public PacketSignal handle(AvailableEntityIdentifiersPacket packet) {
        proxy.saveNBT("entity_identifiers", packet.getIdentifiers());
        return PacketSignal.UNHANDLED;
    }

    public PacketSignal handle(BiomeDefinitionListPacket packet) {
        proxy.saveNBT("biome_definitions", packet.getDefinitions());
        return PacketSignal.UNHANDLED;
    }

    public PacketSignal handle(StartGamePacket packet) {
        List<DataEntry> itemData = new ArrayList<>();
        LinkedHashMap<String, Integer> legacyItems = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> legacyBlocks = new LinkedHashMap<>();

        for (ItemDefinition entry : packet.getItemDefinitions()) {
            if (entry.getRuntimeId() > 255) {
                legacyItems.putIfAbsent(entry.getIdentifier(), entry.getRuntimeId());
            } else {
                String id = entry.getIdentifier();
                if (id.contains(":item.")) {
                    id = id.replace(":item.", ":");
                }
                if (entry.getRuntimeId() > 0) {
                    legacyBlocks.putIfAbsent(id, (int) entry.getRuntimeId());
                } else {
                    legacyBlocks.putIfAbsent(id, 255 - entry.getRuntimeId());
                }
            }

            itemData.add(new DataEntry(entry.getIdentifier(), entry.getRuntimeId()));
            ProxyPass.legacyIdMap.put(entry.getRuntimeId(), entry.getIdentifier());
        }

        this.session.getPeer().getCodecHelper().setItemDefinitions(SimpleDefinitionRegistry.<ItemDefinition>builder()
                .addAll(packet.getItemDefinitions())
                .build());

        itemData.sort(Comparator.comparing(o -> o.name));

        proxy.saveJson("legacy_block_ids.json", sortMap(legacyBlocks));
        proxy.saveJson("legacy_item_ids.json", sortMap(legacyItems));
        proxy.saveJson("runtime_item_states.json", itemData);

        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(CraftingDataPacket packet) {
        RecipeUtils.writeRecipes(packet, this.proxy);
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(DisconnectPacket packet) {
        this.session.disconnect();
        // Let the client see the reason too.
        return PacketSignal.UNHANDLED;
    }

    private void dumpCreativeItems(ItemData[] contents) {
        // Load up block palette for conversion, if we can find it.
        Object object = proxy.loadGzipNBT("block_palette.nbt");
        List<NbtMap> paletteTags = null;
        if (object instanceof NbtMap) {
            NbtMap map = (NbtMap) object;
            paletteTags = map.getList("blocks", NbtType.COMPOUND);
        } else {
            log.warn("Failed to load block palette for creative content dump. Output will contain block runtime IDs!");
        }

        List<CreativeItemEntry> entries = new ArrayList<>();
        for (ItemData data : contents) {
            ItemDefinition entry = data.getDefinition();
            String id = entry.getIdentifier();
            Integer damage = data.getDamage() == 0 ? null : (int) data.getDamage();

            String blockTag = null;
            Integer blockRuntimeId = null;
            if (data.getBlockRuntimeId() != 0 && paletteTags != null) {
                blockTag = encodeNbtToString(paletteTags.get(data.getBlockRuntimeId()));
            } else if (data.getBlockRuntimeId() != 0) {
                blockRuntimeId = data.getBlockRuntimeId();
            }

            NbtMap tag = data.getTag();
            String tagData = null;
            if (tag != null) {
                tagData = encodeNbtToString(tag);
            }
            entries.add(new CreativeItemEntry(id, damage, blockRuntimeId, blockTag, tagData));
        }

        CreativeItems items = new CreativeItems(entries);

        proxy.saveJson("creative_items.json", items);
    }

    @Override
    public PacketSignal handle(CreativeContentPacket packet) {
        dumpCreativeItems(packet.getContents());
        return PacketSignal.UNHANDLED;
    }

    // Pre 1.16 method of Creative Items
    @Override
    public PacketSignal handle(InventoryContentPacket packet) {
        if (packet.getContainerId() == ContainerId.CREATIVE) {
            dumpCreativeItems(packet.getContents().toArray(new ItemData[0]));
        }
        return PacketSignal.UNHANDLED;
    }

    private static Map<String, Integer> sortMap(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        Map<String, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
        return sortedMap;
    }

    private static String encodeNbtToString(NbtMap tag) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (NBTOutputStream stream = new NBTOutputStream(new LittleEndianDataOutputStream(byteArrayOutputStream))) {
            stream.writeTag(tag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CreativeItemEntry {
        private final String id;
        private final Integer damage;
        private final Integer blockRuntimeId;
        @JsonProperty("block_state_b64")
        private final String blockTag;
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
