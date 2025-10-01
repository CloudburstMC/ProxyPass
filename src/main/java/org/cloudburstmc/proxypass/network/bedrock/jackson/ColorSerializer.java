package org.cloudburstmc.proxypass.network.bedrock.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.awt.*;
import java.io.IOException;

public class ColorSerializer extends StdSerializer<Color> {

    public ColorSerializer() {
        super(Color.class);
    }

    @Override
    public void serialize(Color color, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if (color == null) {
            jsonGenerator.writeNull();
        } else {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeNumberField("a", color.getAlpha());
            jsonGenerator.writeNumberField("r", color.getRed());
            jsonGenerator.writeNumberField("g", color.getGreen());
            jsonGenerator.writeNumberField("b", color.getBlue());
            jsonGenerator.writeEndObject();
        }
    }
}
