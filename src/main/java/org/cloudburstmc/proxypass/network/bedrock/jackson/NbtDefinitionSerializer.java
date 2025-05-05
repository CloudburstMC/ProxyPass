package org.cloudburstmc.proxypass.network.bedrock.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import lombok.extern.slf4j.Slf4j;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.util.stream.LittleEndianDataOutputStream;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
public class NbtDefinitionSerializer extends StdSerializer<NbtBlockDefinitionRegistry.NbtBlockDefinition> {
    public NbtDefinitionSerializer() {
        super(NbtBlockDefinitionRegistry.NbtBlockDefinition.class);
    }

    @Override
    public void serialize(NbtBlockDefinitionRegistry.NbtBlockDefinition value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             NBTOutputStream stream = new NBTOutputStream(new LittleEndianDataOutputStream(byteArrayOutputStream))) {
            stream.writeTag(value.tag());
            gen.writeObjectField("block_state_b64", Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()));
        } catch (IOException e) {
            log.error("Failed to serialize NBT block definition", e);
            gen.writeNull();
        } finally {
            gen.writeEndObject();
        }
    }
}
