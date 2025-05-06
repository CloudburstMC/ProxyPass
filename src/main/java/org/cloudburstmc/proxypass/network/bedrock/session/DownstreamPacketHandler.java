package org.cloudburstmc.proxypass.network.bedrock.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.util.stream.LittleEndianDataOutputStream;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionChunkGenData;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitionData;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.CreativeItemGroup;
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
        if (packet.getDefinitions() != null) {
            proxy.saveNBT("biome_definitions", packet.getDefinitions());
        }
        if (packet.getBiomes() != null) {
            Map<String, BiomeDefinitionData> definitions = packet.getBiomes().getDefinitions();
            proxy.saveJson("biome_definitions.json", packet.getBiomes().getDefinitions());
            Map<String, BiomeDefinitionData> strippedDefinitions = new LinkedHashMap<>();
            for (Map.Entry<String, BiomeDefinitionData> entry : definitions.entrySet()) {
                String id = entry.getKey();
                BiomeDefinitionData data = entry.getValue();

                strippedDefinitions.put(id, new BiomeDefinitionData(data.getId(), data.getTemperature(),
                        data.getDownfall(), data.getRedSporeDensity(), data.getBlueSporeDensity(), data.getAshDensity(),
                        data.getWhiteAshDensity(), data.getDepth(), data.getScale(), data.getMapWaterColor(),
                        data.isRain(), data.getTags(), null));
            }
            proxy.saveJson("stripped_biome_definitions.json", strippedDefinitions);
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(StartGamePacket packet) {
        if (ProxyPass.CODEC.getProtocolVersion() < 776) {
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

                itemData.add(new DataEntry(entry.getIdentifier(), entry.getRuntimeId(), -1, false));
                ProxyPass.legacyIdMap.put(entry.getRuntimeId(), entry.getIdentifier());
            }

            SimpleDefinitionRegistry<ItemDefinition> itemDefinitions = SimpleDefinitionRegistry.<ItemDefinition>builder()
                    .addAll(packet.getItemDefinitions())
                    .add(new SimpleItemDefinition("minecraft:empty", 0, false))
                    .build();

            this.session.getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);
            player.getUpstream().getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);

            itemData.sort(Comparator.comparing(o -> o.name));

            proxy.saveJson("legacy_block_ids.json", sortMap(legacyBlocks));
            proxy.saveJson("legacy_item_ids.json", sortMap(legacyItems));
            proxy.saveJson("runtime_item_states.json", itemData);
        }


        DefinitionRegistry<BlockDefinition> registry;
        if (packet.isBlockNetworkIdsHashed()) {
            registry = this.proxy.getBlockDefinitionsHashed();
        } else {
            registry = this.proxy.getBlockDefinitions();
        }

        this.session.getPeer().getCodecHelper().setBlockDefinitions(registry);
        player.getUpstream().getPeer().getCodecHelper().setBlockDefinitions(registry);

        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(ItemComponentPacket packet) {
        List<DataEntry> itemData = new ArrayList<>();

        NbtMapBuilder root = NbtMap.builder();
        for (var item : packet.getItems()) {
            root.putCompound(item.getIdentifier(), item.getComponentData());
            itemData.add(new DataEntry(item.getIdentifier(), item.getRuntimeId(), item.getVersion().ordinal(), item.isComponentBased()));
        }

        if (ProxyPass.CODEC.getProtocolVersion() >= 776) {
            SimpleDefinitionRegistry.Builder<ItemDefinition> builder = SimpleDefinitionRegistry.<ItemDefinition>builder()
                    .add(new SimpleItemDefinition("minecraft:empty", 0, false));


            for (DataEntry entry : itemData) {
                ProxyPass.legacyIdMap.put(entry.getId(), entry.getName());
                builder.add(new SimpleItemDefinition(entry.getName(), entry.getId(), false));
            }

            SimpleDefinitionRegistry<ItemDefinition> itemDefinitions = builder.build();

            this.session.getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);
            player.getUpstream().getPeer().getCodecHelper().setItemDefinitions(itemDefinitions);

            itemData.sort(Comparator.comparing(o -> o.name));
            proxy.saveJson("runtime_item_states.json", itemData);
        }

        proxy.saveCompressedNBT("item_components", root.build());

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

    private void dumpCreativeItems(List<CreativeItemGroup> groups, List<CreativeItemData> contents) {
        List<CreativeGroup> groupEntries = new ArrayList<>();
        for (CreativeItemGroup group : groups) {
            String categoryName = group.getCategory().name().toLowerCase();
            String name = group.getName();
            groupEntries.add(new CreativeGroup(name, categoryName, createCreativeItemEntry(group.getIcon())));
        }

        List<CreativeItemEntry> entries = new ArrayList<>();
        for (CreativeItemData content : contents) {
            entries.add(createCreativeItemEntry(content.getItem(), content.getGroupId()));
        }

        Map<String, Object> items = new HashMap<>();
        items.put("groups", groupEntries);
        items.put("items", entries);

        proxy.saveJson("creative_items.json", items);
    }

    private CreativeItemEntry createCreativeItemEntry(ItemData data, int groupId) {
        ItemEntry entry = createCreativeItemEntry(data);
        return new CreativeItemEntry(entry.getId(), entry.getDamage(), entry.getBlockRuntimeId(), entry.getBlockTag(), entry.getNbt(), groupId);
    }

    private ItemEntry createCreativeItemEntry(ItemData data) {
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
        return new ItemEntry(id, damage, blockRuntimeId, blockTag, tagData);
    }

    @Override
    public PacketSignal handle(CreativeContentPacket packet) {
        try {
            dumpCreativeItems(packet.getGroups(), packet.getContents());
        } catch (Exception e) {
            log.error("Failed to dump creative contents", e);
        }
        return PacketSignal.UNHANDLED;
    }

    // Handles creative items for versions prior to 1.16
//    @SuppressWarnings("deprecation")
//    @Override
//    public PacketSignal handle(InventoryContentPacket packet) {
//        if (packet.getContainerId() == ContainerId.CREATIVE) {
//            dumpCreativeItems(packet.getContents().toArray(new ItemData[0]));
//        }
//        return PacketSignal.UNHANDLED;
//    }

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
    private static class CreativeItemEntry extends ItemEntry {
        private final int groupId;

        public CreativeItemEntry(String id, Integer damage, Integer blockRuntimeId, String blockTag, String nbt, int groupId) {
            super(id, damage, blockRuntimeId, blockTag, nbt);
            this.groupId = groupId;
        }
    }

    @Data
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class ItemEntry {
        private final String id;
        private final Integer damage;
        private final Integer blockRuntimeId;
        @JsonProperty("block_state_b64")
        private final String blockTag;
        @JsonProperty("nbt_b64")
        private final String nbt;
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CreativeGroup {
        private final String name;
        private final String category;
        private final ItemEntry icon;
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
        private final int version;
        private final boolean componentBased;
    }
}
