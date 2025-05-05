package org.cloudburstmc.proxypass.network.bedrock.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.awt.*;
import java.io.IOException;

public class ColorDeserializer extends StdDeserializer<Color> {

    public ColorDeserializer() {
        super(Color.class);
    }

    @Override
    public Color deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        if (jsonParser.getCurrentToken().isStructStart()) {
            int alpha = 255;
            int red = 0;
            int green = 0;
            int blue = 0;

            while (jsonParser.nextToken() != null) {
                String fieldName = jsonParser.getCurrentName();
                jsonParser.nextToken();
                switch (fieldName) {
                    case "a" -> alpha = jsonParser.getIntValue();
                    case "r" -> red = jsonParser.getIntValue();
                    case "g" -> green = jsonParser.getIntValue();
                    case "b" -> blue = jsonParser.getIntValue();
                }
            }
            return new Color(red, green, blue, alpha);
        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_NULL) {
            return null;
        } else {
            throw deserializationContext.wrongTokenException(jsonParser, Color.class, JsonToken.START_OBJECT, "Expected a color object");
        }
    }
}
