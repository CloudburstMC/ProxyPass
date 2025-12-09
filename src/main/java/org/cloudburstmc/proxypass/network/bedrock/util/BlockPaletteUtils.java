package org.cloudburstmc.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.cloudburstmc.nbt.*;
import org.cloudburstmc.proxypass.ProxyPass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unchecked")
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

    public static int createHash(NbtMap block) {
        if (block.getString("name").equals("minecraft:unknown")) {
            return -2; // This is special case
        }
        // Order required
        TreeMap<String, Object> states = new TreeMap<>(block.getCompound("states"));
        NbtMapBuilder statesBuilder = NbtMap.builder();
        statesBuilder.putAll(states);

        NbtMap tag = NbtMap.builder()
                .putString("name", block.getString("name"))
                .putCompound("states", statesBuilder.build())
                .build();

        byte[] bytes;
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             NBTOutputStream outputStream = NbtUtils.createWriterLE(stream)) {
            outputStream.writeTag(tag);
            bytes = stream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return fnv1a_32(bytes);
    }

    private static final int FNV1_32_INIT = 0x811c9dc5;
    private static final int FNV1_PRIME_32 = 0x01000193;

    private static int fnv1a_32(byte[] data) {
        int hash = FNV1_32_INIT;
        for (byte datum : data) {
            hash ^= (datum & 0xff);
            hash *= FNV1_PRIME_32;
        }
        return hash;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"id", "meta", "name", "states"})
    private record Entry(int id, Integer meta, String name, Map<String, BlockState> states) {
    }

    @JsonPropertyOrder({"val", "type"})
    private record BlockState(Object val, int type) {
    }
}
