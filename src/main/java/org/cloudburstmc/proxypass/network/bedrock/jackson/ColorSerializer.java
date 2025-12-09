package org.cloudburstmc.proxypass.network.bedrock.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.awt.*;

public class ColorSerializer extends StdSerializer<Color> {

    public ColorSerializer() {
        super(Color.class);
    }

    @Override
    public void serialize(Color value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeStartObject();
            gen.writeNumberProperty("a", value.getAlpha());
            gen.writeNumberProperty("r", value.getRed());
            gen.writeNumberProperty("g", value.getGreen());
            gen.writeNumberProperty("b", value.getBlue());
            gen.writeEndObject();
        }
    }
}
