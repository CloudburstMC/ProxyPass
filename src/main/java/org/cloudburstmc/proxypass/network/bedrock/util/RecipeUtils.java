package org.cloudburstmc.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.ContainerMixData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.CraftingDataType;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.PotionMixData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.*;
import org.cloudburstmc.protocol.bedrock.data.inventory.descriptor.*;
import org.cloudburstmc.protocol.bedrock.packet.CraftingDataPacket;
import org.cloudburstmc.proxypass.ProxyPass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@UtilityClass
public class RecipeUtils {
    private static final char[] SHAPE_CHARS = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'};

    public static void writeRecipes(CraftingDataPacket packet, ProxyPass proxy) {
        List<CraftingDataEntry> entries = new ArrayList<>();
        List<PotionMixDataEntry> potions = new ArrayList<>();
        List<ContainerMixDataEntry> containers = new ArrayList<>();

        for (RecipeData recipe : packet.getCraftingData()) {
            CraftingDataEntry entry = new CraftingDataEntry();

            CraftingDataType type = recipe.getType();
            entry.type = type.ordinal();

            if (recipe instanceof TaggedCraftingData taggedRecipe) {
                entry.block = taggedRecipe.getTag();
            } else if (recipe instanceof UniqueCraftingData uniqueRecipe) {
                entry.uuid = uniqueRecipe.getUuid();
            }

            if (recipe instanceof CraftingRecipeData craftingRecipe) {
                entry.id = craftingRecipe.getId();
                entry.priority = craftingRecipe.getPriority();
                entry.output = writeItemArray(craftingRecipe.getResults().toArray(new ItemData[0]));
            }
            if (recipe instanceof ShapedRecipeData shapedRecipe) {

                int charCounter = 0;
                // ItemData[] inputs = craftingData.getInputs().toArray(new ItemData[0]);
                List<ItemDescriptorWithCount> inputs = shapedRecipe.getIngredients();
                Map<Descriptor, Character> charItemMap = new HashMap<>();
                char[][] shape = new char[shapedRecipe.getHeight()][shapedRecipe.getWidth()];

                for (int height = 0; height < shapedRecipe.getHeight(); height++) {
                    Arrays.fill(shape[height], ' ');
                    int index = height * shapedRecipe.getWidth();
                    for (int width = 0; width < shapedRecipe.getWidth(); width++) {
                        int slot = index + width;
                        Descriptor descriptor = fromNetwork(inputs.get(slot));

                        if (ItemDescriptorType.INVALID.name().toLowerCase().equals(descriptor.getType())) {
                            continue;
                        }

                        Character shapeChar = charItemMap.get(descriptor);
                        if (shapeChar == null) {
                            shapeChar = SHAPE_CHARS[charCounter++];
                            charItemMap.put(descriptor, shapeChar);
                        }

                        shape[height][width] = shapeChar;
                    }
                }

                String[] shapeString = new String[shape.length];
                for (int i = 0; i < shape.length; i++) {
                    shapeString[i] = new String(shape[i]);
                }
                entry.shape = shapeString;

                Map<Character, Descriptor> itemMap = new HashMap<>();
                for (Map.Entry<Descriptor, Character> mapEntry : charItemMap.entrySet()) {
                    itemMap.put(mapEntry.getValue(), mapEntry.getKey());
                }
                entry.input = itemMap;
            }
            if (recipe instanceof ShapelessRecipeData shapelessRecipe) {
                entry.input = writeDescriptorArray(shapelessRecipe.getIngredients());
            }

            if (recipe instanceof FurnaceRecipeData furnaceRecipe) {
                Integer damage = furnaceRecipe.getInputData();
                if (damage == 0x7fff) damage = -1;
                if (damage == 0) damage = null;
                entry.input = new Item(furnaceRecipe.getInputId(), ProxyPass.legacyIdMap.get(furnaceRecipe.getInputId()), damage, null, null);
                entry.output = itemFromNetwork(furnaceRecipe.getResult());
            }
            entries.add(entry);
        }
        for (PotionMixData potion : packet.getPotionMixData()) {
            potions.add(new PotionMixDataEntry(
                    ProxyPass.legacyIdMap.get(potion.getInputId()),
                    potion.getInputMeta(),
                    ProxyPass.legacyIdMap.get(potion.getReagentId()),
                    potion.getReagentMeta(),
                    ProxyPass.legacyIdMap.get(potion.getOutputId()),
                    potion.getOutputMeta()
            ));
        }

        for (ContainerMixData container : packet.getContainerMixData()) {
            containers.add(new ContainerMixDataEntry(
                    ProxyPass.legacyIdMap.get(container.getInputId()),
                    ProxyPass.legacyIdMap.get(container.getReagentId()),
                    ProxyPass.legacyIdMap.get(container.getOutputId())
            ));
        }

        Recipes recipes = new Recipes(ProxyPass.CODEC.getProtocolVersion(), entries, potions, containers);

        proxy.saveJson("recipes.json", recipes);
    }

    private static List<Item> writeItemArray(ItemData[] inputs) {
        List<Item> outputs = new ArrayList<>();
        for (ItemData input : inputs) {
            Item item = itemFromNetwork(input);
            if (item != Item.EMPTY) {
                outputs.add(item);
            }
        }
        return outputs;
    }

    private static List<Descriptor> writeDescriptorArray(List<ItemDescriptorWithCount> inputs) {
        List<Descriptor> outputs = new ArrayList<>();
        for (ItemDescriptorWithCount input : inputs) {
            Descriptor descriptor = fromNetwork(input);
            if (!ItemDescriptorType.INVALID.name().toLowerCase().equals(descriptor.getType())) {
                outputs.add(descriptor);
            }
        }
        return outputs;
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

    private static Item itemFromNetwork(ItemData data) {
        int id = data.getDefinition().getRuntimeId();
        String identifier = ProxyPass.legacyIdMap.get(id);
        Integer damage = data.getDamage();
        Integer count = data.getCount();
        String tag = nbtToBase64(data.getTag());

        if (id == 0) {
            return Item.EMPTY;
        }
        if (damage == 0 || damage == -1) damage = null;
        if (count == 1) count = null;

        return new Item(id, identifier, damage, count, tag);
    }

    private static Descriptor fromNetwork(ItemDescriptorWithCount descriptorWithCount) {
        Descriptor descriptor = new Descriptor();
        descriptor.setType(descriptorWithCount.getDescriptor().getType().name().toLowerCase());
        descriptor.setCount(descriptorWithCount.getCount());
        ItemDescriptor itemDescriptor = descriptorWithCount.getDescriptor();

        if (itemDescriptor instanceof DefaultDescriptor) {
            descriptor.setItemId(((DefaultDescriptor) itemDescriptor).getItemId().getRuntimeId());
            descriptor.setAuxValue(((DefaultDescriptor) itemDescriptor).getAuxValue());
        } else if (itemDescriptor instanceof MolangDescriptor) {
            descriptor.setTagExpression(((MolangDescriptor) itemDescriptor).getTagExpression());
            descriptor.setMolangVersion(((MolangDescriptor) itemDescriptor).getMolangVersion());
        } else if (itemDescriptor instanceof ItemTagDescriptor) {
            descriptor.setItemTag(((ItemTagDescriptor) itemDescriptor).getItemTag());
        } else if (itemDescriptor instanceof DeferredDescriptor) {
            descriptor.setFullName(((DeferredDescriptor) itemDescriptor).getFullName());
            descriptor.setAuxValue(((DeferredDescriptor) itemDescriptor).getAuxValue());
        }
        return descriptor;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "type", "input", "output", "shape", "block", "uuid", "priority"})
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

    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"inputId", "inputMeta", "reagentId", "reagentMeta", "outputId", "outputMeta"})
    private static class PotionMixDataEntry {
        private String inputId;
        private int inputMeta;
        private String reagentId;
        private int reagentMeta;
        private String outputId;
        private int outputMeta;
    }

    @AllArgsConstructor
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"inputId", "reagentId", "outputId"})
    private static class ContainerMixDataEntry {
        private String inputId;
        private String reagentId;
        private String outputId;
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"legacyId", "id", "damage", "count", "nbt_b64"})
    private static class Item {
        public static final Item EMPTY = new Item(0, "minecraft:air", null, null, null);

        int legacyId;
        String id;
        Integer damage;
        Integer count;
        String nbt_b64;
    }

    @Value
    @JsonPropertyOrder({"version", "recipes", "potionMixes", "containerMixes"})
    private static class Recipes {
        int version;
        List<CraftingDataEntry> recipes;
        List<PotionMixDataEntry> potionMixes;
        List<ContainerMixDataEntry> containerMixes;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"type", "count", "itemId", "auxValue", "fullName", "itemTag", "tagExpression", "molangVersion"})
    private static class Descriptor {
        String type;
        int count;
        // Default descriptor
        Integer itemId;
        Integer auxValue;
        // Deferred descriptor
        String fullName;
        // Item tag descriptor
        String itemTag;
        // Molang descriptor
        String tagExpression;
        Integer molangVersion;
    }
}
