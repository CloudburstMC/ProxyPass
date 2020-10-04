package com.nukkitx.proxypass;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nukkitx.proxypass.network.bedrock.util.LogTo;
import lombok.Getter;
import lombok.ToString;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;

@Getter
@ToString
public class Configuration {

    private Address proxy;
    private Address destination;

    @JsonProperty("packet-testing")
    private boolean packetTesting = false;
    @JsonProperty("log-packets")
    private boolean loggingPackets = false;
    @JsonProperty("max-clients")
    private int maxClients = 0;
    @JsonProperty("log-to")
    private LogTo logTo = LogTo.FILE;

    @JsonProperty("ignored-packets")
    private Set<String> ignoredPackets = Collections.emptySet();

    public static Configuration load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return ProxyPass.YAML_MAPPER.readValue(reader, Configuration.class);
        }
    }

    public static Configuration load(InputStream stream) throws IOException {
        return ProxyPass.YAML_MAPPER.readValue(stream, Configuration.class);
    }

    public static void save(Path path, Configuration configuration) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ProxyPass.YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, configuration);
        }
    }

    @Getter
    @ToString
    public static class Address {
        private String host;
        private int port;

        InetSocketAddress getAddress() {
            return new InetSocketAddress(host, port);
        }
    }
}
