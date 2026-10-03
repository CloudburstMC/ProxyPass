package org.cloudburstmc.proxypass.network.bedrock.jackson;

import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.awt.*;

public class OptionalBooleanSerializer extends StdSerializer<OptionalBoolean> {

    public OptionalBooleanSerializer() {
        super(OptionalBoolean.class);
    }

    @Override
    public void serialize(OptionalBoolean value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        if (value == null || !value.isPresent()) {
            gen.writeNull();
        } else {
            gen.writeBoolean(value.getAsBoolean());
        }
    }
}
