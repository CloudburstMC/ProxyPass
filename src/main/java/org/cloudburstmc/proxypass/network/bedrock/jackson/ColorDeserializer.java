package org.cloudburstmc.proxypass.network.bedrock.jackson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.awt.*;

public class ColorDeserializer extends StdDeserializer<Color> {

    public ColorDeserializer() {
        super(Color.class);
    }

    @Override
    public Color deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
        if (jsonParser.currentToken().isStructStart()) {
            int alpha = 255;
            int red = 0;
            int green = 0;
            int blue = 0;

            while (jsonParser.nextToken() != null) {
                String fieldName = jsonParser.currentName();
                jsonParser.nextToken();
                switch (fieldName) {
                    case "a" -> alpha = jsonParser.getIntValue();
                    case "r" -> red = jsonParser.getIntValue();
                    case "g" -> green = jsonParser.getIntValue();
                    case "b" -> blue = jsonParser.getIntValue();
                }
            }
            return new Color(red, green, blue, alpha);
        } else if (jsonParser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        } else {
            throw deserializationContext.wrongTokenException(jsonParser, Color.class, JsonToken.START_OBJECT, "Expected a color object");
        }
    }
}
