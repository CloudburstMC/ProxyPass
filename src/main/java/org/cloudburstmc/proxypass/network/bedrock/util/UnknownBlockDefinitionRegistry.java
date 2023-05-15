package org.cloudburstmc.proxypass.network.bedrock.util;

import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.common.DefinitionRegistry;

public class UnknownBlockDefinitionRegistry implements DefinitionRegistry<BlockDefinition> {

    @Override
    public BlockDefinition getDefinition(int runtimeId) {
        return new UnknownDefinition(runtimeId);
    }

    @Override
    public boolean isRegistered(BlockDefinition blockDefinition) {
        return true;
    }

    private record UnknownDefinition(int runtimeId) implements BlockDefinition {

        @Override
        public int getRuntimeId() {
            return runtimeId;
        }
    }
}
