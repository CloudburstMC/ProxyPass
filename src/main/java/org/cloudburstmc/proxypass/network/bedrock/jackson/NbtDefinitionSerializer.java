package org.cloudburstmc.proxypass.network.bedrock.jackson;

import lombok.extern.slf4j.Slf4j;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.util.stream.LittleEndianDataOutputStream;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry.NbtBlockDefinition;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
public class NbtDefinitionSerializer extends StdSerializer<NbtBlockDefinition> {

    public NbtDefinitionSerializer() {
        super(NbtBlockDefinition.class);
    }

    @Override
    public void serialize(NbtBlockDefinition value, JsonGenerator gen, SerializationContext provider) throws JacksonException {
        gen.writeStartObject();
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             NBTOutputStream stream = new NBTOutputStream(new LittleEndianDataOutputStream(byteArrayOutputStream))) {
            stream.writeTag(value.tag());
            gen.writeStringProperty("block_state_b64", Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()));
        } catch (IOException e) {
            log.error("Failed to serialize NBT block definition", e);
            gen.writeNull();
        } finally {
            gen.writeEndObject();
        }
    }
}
