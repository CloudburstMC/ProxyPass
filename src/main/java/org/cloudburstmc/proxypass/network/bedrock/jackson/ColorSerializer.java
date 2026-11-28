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
            gen.writeNumberProperty("alpha", value.getAlpha());
            gen.writeNumberProperty("red", value.getRed());
            gen.writeNumberProperty("green", value.getGreen());
            gen.writeNumberProperty("blue", value.getBlue());
            gen.writeEndObject();
        }
    }
}
