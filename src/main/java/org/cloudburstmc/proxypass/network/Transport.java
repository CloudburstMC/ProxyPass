package org.cloudburstmc.proxypass.network;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@RequiredArgsConstructor
public enum Transport {
    RAKNET(true),
    NETHERNET(false);

    private final boolean packetEncryption;

    public boolean supportsPacketEncryption() {
        return this.packetEncryption;
    }

    @JsonCreator
    public static Transport parse(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String identifier() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
