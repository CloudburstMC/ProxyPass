package org.cloudburstmc.proxypass;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.util.LogTo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

@Getter
@Setter
@ToString
public class Configuration {

    @JsonProperty("transport")
    private Transport transport = Transport.NETHERNET;
    @JsonProperty("proxy")
    private Address proxy;
    @JsonProperty("destination")
    private Address destination;
    @JsonProperty("nethernet")
    private NetherNet nethernet = new NetherNet();

    @JsonProperty("packet-testing")
    private boolean packetTesting = false;
    @JsonProperty("log-packets")
    private boolean loggingPackets = false;
    @JsonProperty("max-clients")
    private int maxClients = 0;
    @JsonProperty("log-to")
    private LogTo logTo = LogTo.FILE;
    @JsonProperty("ignore-resource-packs")
    private boolean ignoreResourcePacks = false;

    @JsonProperty("ignored-packets")
    private Set<String> ignoredPackets = Collections.emptySet();
    @JsonProperty("blocked-packets")
    private Set<String> blockedPackets = Collections.emptySet();

    @JsonProperty("online-mode")
    private boolean onlineMode = true;
    @JsonProperty("save-auth-details")
    private boolean saveAuthDetails = true;
    @JsonProperty("default-account-name")
    private String defaultAccountName = "";

    public static Configuration load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            Configuration configuration = ProxyPass.YAML_MAPPER.readValue(reader, Configuration.class);
            configuration.validate();
            return configuration;
        }
    }

    public static Configuration load(InputStream stream) throws IOException {
        Configuration configuration = ProxyPass.YAML_MAPPER.readValue(stream, Configuration.class);
        configuration.validate();
        return configuration;
    }

    public static void save(Path path, Configuration configuration) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ProxyPass.YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, configuration);
        }
    }

    @Getter
    @ToString
    public static class Address {
        @JsonProperty("host")
        private String host;
        @JsonProperty("port")
        private int port;
        @JsonProperty("signaling-scheme")
        private String signalingScheme = "auto";
        @JsonProperty("server-public-key")
        private String serverPublicKey;

        public InetSocketAddress getAddress() {
            return new InetSocketAddress(host, port);
        }

        public static Address from(InetSocketAddress address) {
            Address a = new Address();
            a.host = address.getHostString();
            a.port = address.getPort();
            return a;
        }

        public HttpSignalingSettings signalingSettings() {
            return HttpSignalingSettings.DEFAULT.withScheme(
                    HttpSignalingSettings.Scheme.valueOf(this.signalingScheme.toUpperCase(Locale.ROOT)));
        }
    }

    @Getter
    public static class NetherNet {
        @JsonProperty("identity-file")
        private String identityFile = "nethernet/identity.pem";
        @JsonProperty("verify-client-authentication")
        private boolean verifyClientAuthentication = true;
        @JsonProperty("tls-certificate")
        private String tlsCertificate;
        @JsonProperty("tls-private-key")
        private String tlsPrivateKey;
    }

    public void validate() throws IOException {
        if (this.transport == null || this.destination == null || this.nethernet == null || this.maxClients < 0) {
            throw new IOException("Invalid proxy configuration");
        }

        validateAddress(this.proxy);
        validateAddress(this.destination);
        InetSocketAddress listener = this.proxy.getAddress();
        if (this.transport == Transport.NETHERNET && listener.getAddress() != null && listener.getAddress().isLoopbackAddress()) {
            throw new IOException("NetherNet cannot accept retail clients on a loopback address. Set proxy.host to a local network address or 0.0.0.0");
        }

        if (this.nethernet.identityFile == null || this.nethernet.identityFile.isBlank() || (this.nethernet.tlsCertificate == null) != (this.nethernet.tlsPrivateKey == null)) {
            throw new IOException("NetherNet requires an identity path and paired TLS certificate and key paths");
        }
    }

    private static void validateAddress(Address address) throws IOException {
        if (address == null || address.host == null || address.host.isBlank() || address.port < 1 || address.port > 65535) {
            throw new IOException("Every endpoint requires a host and a valid port");
        }

        try {
            address.signalingSettings();
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new IOException("Signaling scheme must be auto, http or https", failure);
        }
    }
}
