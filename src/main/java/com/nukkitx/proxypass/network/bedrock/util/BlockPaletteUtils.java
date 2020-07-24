package com.nukkitx.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtType;
import com.nukkitx.proxypass.ProxyPass;
import lombok.Value;

import java.util.*;

public class BlockPaletteUtils {

    public static void convertToJson(ProxyPass proxy, List<NbtMap> tags) {

        List<Entry> palette = new ArrayList<>(tags.size());

        for (NbtMap tag : tags) {
            int id = tag.getShort("id");
            NbtMap blockTag = tag.getCompound("block");
            String name = blockTag.getString("name");

            Map<String, BlockState> states = new LinkedHashMap<>();

            blockTag.getCompound("states").forEach((key, value) -> {
                states.put(key, new BlockState(value, NbtType.byClass(value.getClass()).getId()));
            });

            Integer meta = null;
            if (tag.containsKey("meta")) {
                meta = (int) tag.getShort("meta");
            }
            palette.add(new Entry(id, meta, name, states));
        }
        palette.sort((o1, o2) -> {
            int compare = Integer.compare(o1.id, o2.id);
            if (compare == 0) {
                for (Map.Entry<String, BlockState> entry : o1.states.entrySet()) {
                    BlockState bs2 = o2.states.get(entry.getKey());
                    compare = ((Comparable) entry.getValue().val).compareTo(bs2.val);
                    if (compare != 0) {
                        break;
                    }
                }
            }
            return compare;
        });


        proxy.saveJson("runtime_block_states.json", palette);

        // Get all block states
        Map<String, Set<Object>> blockTraits = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (NbtMap tag : tags) {
            NbtMap map = tag.getCompound("block").getCompound("states");
            map.forEach((trait, value) -> {
                blockTraits.computeIfAbsent(trait, s -> new HashSet<>())
                        .add(value);
            });
        }

        proxy.saveJson("block_traits.json", blockTraits);
    }

    @Value
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static class Entry {
        private final int id;
        private final Integer meta;
        private final String name;
        private final Map<String, BlockState> states;
    }

    @Value
    private static class BlockState {
        private Object val;
        private int type;
    }
}
