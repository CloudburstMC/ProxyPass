package org.cloudburstmc.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

import java.util.List;

public class NbtBlockDefinitionRegistry implements DefinitionRegistry<BlockDefinition> {

    private final Int2ObjectMap<NbtBlockDefinition> definitions = new Int2ObjectOpenHashMap<>();

    public NbtBlockDefinitionRegistry(List<NbtMap> definitions, boolean hashed) {
        int counter = 0;
        for (NbtMap definition : definitions) {
            int runtimeId = hashed ? BlockPaletteUtils.createHash(definition) : counter++;
            this.definitions.put(runtimeId, new NbtBlockDefinition(runtimeId, definition));
        }
    }

    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        return definitions.get(runtimeId);
    }

    @Override
    public boolean isRegistered(BlockDefinition definition) {
        return definitions.get(definition.getRuntimeId()) == definition;
    }

    public record NbtBlockDefinition(@JsonIgnoreProperties int runtimeId, NbtMap tag) implements BlockDefinition {
        @Override
        @JsonIgnore
        public int getRuntimeId() {
            return runtimeId;
        }
    }
}
