package org.cloudburstmc.proxypass.network.bedrock.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.util.stream.LittleEndianDataOutputStream;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.SimpleDefinitionRegistry;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry;
import org.cloudburstmc.proxypass.network.bedrock.util.RecipeUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
public class DownstreamPacketHandler implements BedrockPacketHandler {
    private final BedrockSession session;
    private final ProxyPlayerSession player;
    private final ProxyPass proxy;

    @Override
    public PacketSignal handle(AvailableEntityIdentifiersPacket packet) {
        proxy.saveNBT("entity_identifiers", packet.getIdentifiers());
        return PacketSignal.UNHANDLED;
    }

    // Handles biome definitions when client-side chunk generation is enabled
    @Override
    public PacketSignal handle(CompressedBiomeDefinitionListPacket packet) {
        proxy.saveNBT("biome_definitions_full", packet.getDefinitions());
        return PacketSignal.UNHANDLED;
    }

    // Handles biome definitions when client-side chunk generation is disabled
    @Override
    public PacketSignal handle(BiomeDefinitionListPacket packet) {
        proxy.saveNBT("biome_definitions", packet.getDefinitions());
        return PacketSignal.UNHANDLED;
    }

    @Override
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
                    legacyBlocks.putIfAbsent(id, entry.getRuntimeId());
                } else {
                    legacyBlocks.putIfAbsent(id, 255 - entry.getRuntimeId());
                }
            }

            itemData.add(new DataEntry(entry.getIdentifier(), entry.getRuntimeId()));
            ProxyPass.legacyIdMap.put(entry.getRuntimeId(), entry.getIdentifier());
        }

        SimpleDefinitionRegistry<ItemDefinition> itemDefinitions = SimpleDefinitionRegistry.<ItemDefinition>builder()
                .addAll(packet.getItemDefinitions())
                .add(new SimpleItemDefinition("minecraft:empty", 0, false))
                .build();

        this.session.getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);
        player.getUpstream().getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);


        DefinitionRegistry<BlockDefinition> registry;
        if (packet.isBlockNetworkIdsHashed()) {
            registry = this.proxy.getBlockDefinitionsHashed();
        } else {
            registry = this.proxy.getBlockDefinitions();
        }

        this.session.getPeer().getCodecHelper().setBlockDefinitions(registry);
        player.getUpstream().getPeer().getCodecHelper().setBlockDefinitions(registry);

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
        List<CreativeItemEntry> entries = new ArrayList<>();
        for (ItemData data : contents) {
            ItemDefinition entry = data.getDefinition();
            String id = entry.getIdentifier();
            Integer damage = data.getDamage() == 0 ? null : (int) data.getDamage();

            String blockTag = null;
            Integer blockRuntimeId = null;
            if (data.getBlockDefinition() instanceof NbtBlockDefinitionRegistry.NbtBlockDefinition definition) {
                blockTag = encodeNbtToString(definition.tag());
            } else if (data.getBlockDefinition() != null) {
                blockRuntimeId = data.getBlockDefinition().getRuntimeId();
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
        try {
            dumpCreativeItems(packet.getContents());
        } catch (Exception e) {
            log.error("Failed to dump creative contents", e);
        }
        return PacketSignal.UNHANDLED;
    }

    // Handles creative items for versions prior to 1.16
    @Override
    public PacketSignal handle(InventoryContentPacket packet) {
        if (packet.getContainerId() == ContainerId.CREATIVE) {
            dumpCreativeItems(packet.getContents().toArray(new ItemData[0]));
        }
        return PacketSignal.UNHANDLED;
    }

    private static Map<String, Integer> sortMap(Map<String, Integer> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue,
                        LinkedHashMap::new));
    }

    private static String encodeNbtToString(NbtMap tag) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             NBTOutputStream stream = new NBTOutputStream(new LittleEndianDataOutputStream(byteArrayOutputStream))) {
            stream.writeTag(tag);
            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
