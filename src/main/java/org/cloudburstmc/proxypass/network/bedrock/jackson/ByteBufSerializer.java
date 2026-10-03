package org.cloudburstmc.proxypass.network.bedrock.jackson;

import io.netty.buffer.ByteBuf;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.awt.*;

public class ByteBufSerializer extends StdSerializer<ByteBuf> {

    public ByteBufSerializer() {
        super(ByteBuf.class);
    }

    @Override
    public void serialize(ByteBuf value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeStartArray();
            int readerIndex = value.readerIndex();
            while (value.isReadable()) {
                gen.writeNumber(value.readByte());
            }
            value.readerIndex(readerIndex);
            gen.writeEndArray();
        }
    }
}
