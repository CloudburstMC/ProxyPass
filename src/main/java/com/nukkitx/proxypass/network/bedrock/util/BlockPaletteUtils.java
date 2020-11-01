package com.nukkitx.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.nbt.NbtType;
import com.nukkitx.proxypass.ProxyPass;
import lombok.Value;

import java.util.*;

public class BlockPaletteUtils {

    public static void convertToJson(ProxyPass proxy, List<NbtMap> tags) {

        List<NbtMap> palette = new ArrayList<>(tags);

        palette.sort((o1, o2) -> {
            int compare = Integer.compare(o1.getShort("id"), o2.getShort("id"));
            if (compare == 0) {
                NbtMap states1 = o1.getCompound("block").getCompound("states");
                NbtMap states2 = o2.getCompound("block").getCompound("states");
                for (Map.Entry<String, Object> entry : states1.entrySet()) {
                    Object bs2 = states2.get(entry.getKey());
                    compare = ((Comparable) entry.getValue()).compareTo(bs2);
                    if (compare != 0) {
                        break;
                    }
                }
            }
            return compare;
        });

        proxy.saveMojangson("runtime_block_states.mojangson", NbtMap.builder()
                .putList("palette", NbtType.COMPOUND, palette)
                .build());

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
