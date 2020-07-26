package com.nukkitx.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nukkitx.nbt.NBTOutputStream;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.protocol.bedrock.data.inventory.*;
import com.nukkitx.protocol.bedrock.packet.CraftingDataPacket;
import com.nukkitx.proxypass.ProxyPass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.experimental.UtilityClass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@UtilityClass
public class RecipeUtils {
    private static final char[] SHAPE_CHARS = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'};

    public static void writeRecipes(CraftingDataPacket packet, ProxyPass proxy) {
        List<CraftingDataEntry> entries = new ArrayList<>();

        for (CraftingData craftingData : packet.getCraftingData()) {
            CraftingDataEntry entry = new CraftingDataEntry();

            CraftingDataType type = craftingData.getType();
            entry.type = type.ordinal();

            if (type != CraftingDataType.MULTI) {
                entry.block = craftingData.getCraftingTag();
            } else {
                entry.uuid = craftingData.getUuid();
            }

            if (type == CraftingDataType.SHAPED || type == CraftingDataType.SHAPELESS || type == CraftingDataType.SHAPELESS_CHEMISTRY || type == CraftingDataType.SHULKER_BOX || type == CraftingDataType.SHAPED_CHEMISTRY) {
                entry.id = craftingData.getRecipeId();
                entry.priority = craftingData.getPriority();
                entry.output = writeItemArray(craftingData.getOutputs(), true);
            }
            if (type == CraftingDataType.SHAPED || type == CraftingDataType.SHAPED_CHEMISTRY) {

                int charCounter = 0;
                ItemData[] inputs = craftingData.getInputs();
                Map<Item, Character> charItemMap = new HashMap<>();
                char[][] shape = new char[craftingData.getHeight()][craftingData.getWidth()];

                for (int height = 0; height < craftingData.getHeight(); height++) {
                    Arrays.fill(shape[height], ' ');
                    int index = height * craftingData.getWidth();
                    for (int width = 0; width < craftingData.getWidth(); width++) {
                        int slot = index + width;
                        Item item = itemFromNetwork(inputs[slot], false);

                        if (item == Item.EMPTY) {
                            continue;
                        }

                        Character shapeChar = charItemMap.get(item);
                        if (shapeChar == null) {
                            shapeChar = SHAPE_CHARS[charCounter++];
                            charItemMap.put(item, shapeChar);
                        }

                        shape[height][width] = shapeChar;
                    }
                }

                String[] shapeString = new String[shape.length];
                for (int i = 0; i < shape.length; i++) {
                    shapeString[i] = new String(shape[i]);
                }
                entry.shape = shapeString;

                Map<Character, Item> itemMap = new HashMap<>();
                for (Map.Entry<Item, Character> mapEntry : charItemMap.entrySet()) {
                    itemMap.put(mapEntry.getValue(), mapEntry.getKey());
                }
                entry.input = itemMap;
            }
            if (type == CraftingDataType.SHAPELESS || type == CraftingDataType.SHAPELESS_CHEMISTRY || type == CraftingDataType.SHULKER_BOX) {
                entry.input = writeItemArray(craftingData.getInputs(), false);
            }

            if (type == CraftingDataType.FURNACE || type == CraftingDataType.FURNACE_DATA) {
                Integer damage = craftingData.getInputDamage();
                if (damage == 0x7fff) damage = -1;
                if (damage == 0) damage = null;
                entry.input = new Item(craftingData.getInputId(), damage, null, null);
                entry.output = itemFromNetwork(craftingData.getOutputs()[0], true);
            }
            entries.add(entry);
        }

        Recipes recipes = new Recipes(ProxyPass.CODEC.getProtocolVersion(), entries, packet.getPotionMixData(), packet.getContainerMixData());

        proxy.saveJson("recipes.json", recipes);
    }

    private static Item[] writeItemArray(ItemData[] inputs, boolean output) {
        List<Item> outputs = new ArrayList<>();
        for (ItemData input : inputs) {
            Item item = itemFromNetwork(input, output);
            if (item != Item.EMPTY) {
                outputs.add(item);
            }
        }
        return outputs.toArray(new Item[0]);
    }

    private static String nbtToBase64(NbtMap tag) {
        if (tag != null) {
            ByteArrayOutputStream tagStream = new ByteArrayOutputStream();
            try (NBTOutputStream writer = NbtUtils.createWriterLE(tagStream)) {
                writer.writeTag(tag);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return Base64.getEncoder().encodeToString(tagStream.toByteArray());
        } else {
            return null;
        }
    }

    private static Item itemFromNetwork(ItemData data, boolean output) {
        int id = data.getId();
        Integer damage = (int) data.getDamage();
        Integer count = data.getCount();
        String tag = nbtToBase64(data.getTag());

        if (id == 0) {
            return Item.EMPTY;
        }
        if (damage == 0 || (damage == -1 && output)) damage = null;
        if (count == 1) count = null;

        return new Item(id, damage, count, tag);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class CraftingDataEntry {
        private String id;
        private int type;
        private Object input;
        private Object output;
        private String[] shape;
        private String block;
        private UUID uuid;
        private Integer priority;
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class Item {
        public static final Item EMPTY = new Item(0, null, null, null);

        private final int id;
        private final Integer damage;
        private final Integer count;
        private final String nbt_b64;
    }

    @Value
    private static class Recipes {
        private final int version;
        private final List<CraftingDataEntry> recipes;
        private final List<PotionMixData> potionMixes;
        private final List<ContainerMixData> containerMixes;
    }
}
