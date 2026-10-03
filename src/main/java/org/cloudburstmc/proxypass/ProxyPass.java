package org.cloudburstmc.proxypass;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ResourceLeakDetector;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.*;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPServerSignaling;
import org.cloudburstmc.netty.channel.nethernet.signaling.PongData;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.ProxyChannelInitializer;
import org.cloudburstmc.proxypass.network.bedrock.jackson.ColorDeserializer;
import org.cloudburstmc.proxypass.network.bedrock.jackson.ColorSerializer;
import org.cloudburstmc.proxypass.network.bedrock.jackson.NbtDefinitionSerializer;
import org.cloudburstmc.proxypass.network.bedrock.logging.SessionLogger;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyClientSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyServerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.UpstreamPacketHandler;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry.NbtBlockDefinition;
import org.cloudburstmc.proxypass.network.bedrock.util.UnknownBlockDefinitionRegistry;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.KeyPair;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Log4j2
@Getter
public class ProxyPass implements AutoCloseable {
    public static final ObjectMapper JSON_MAPPER;
    public static final YAMLMapper YAML_MAPPER;

    private static final SimpleModule MODULE = new SimpleModule("ProxyPass", Version.unknownVersion())
            .addSerializer(Color.class, new ColorSerializer())
            .addDeserializer(Color.class, new ColorDeserializer())
            .addSerializer(NbtBlockDefinition.class, new NbtDefinitionSerializer());

    public static final String MINECRAFT_VERSION;

    public static final BedrockCodec CODEC = Bedrock_v2193.CODEC;
    public static final int PROTOCOL_VERSION = CODEC.getProtocolVersion();

    private static final BedrockPong ADVERTISEMENT = new BedrockPong()
            .edition("MCPE")
            .gameType("Survival")
            .version(ProxyPass.CODEC.getMinecraftVersion())
            .protocolVersion(ProxyPass.PROTOCOL_VERSION)
            .motd("ProxyPass")
            .playerCount(0)
            .maximumPlayerCount(20)
            .subMotd("https://github.com/CloudburstMC/ProxyPass")
            .nintendoLimited(false);

    private static final DefaultPrettyPrinter PRETTY_PRINTER;

    public static Map<Integer, String> legacyIdMap = new HashMap<>();

    static {
        DefaultIndenter indenter = new DefaultIndenter("    ", "\n");
        Separators separators = Separators.createDefaultInstance()
                .withObjectNameValueSpacing(Separators.Spacing.AFTER);

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withSeparators(separators);
        printer.indentArraysWith(indenter);
        printer.indentObjectsWith(indenter);

        PRETTY_PRINTER = printer;

        JSON_MAPPER = JsonMapper.builder()
                .addModule(MODULE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .defaultPrettyPrinter(PRETTY_PRINTER)
                .build();

        YAML_MAPPER = YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        MINECRAFT_VERSION = CODEC.getMinecraftVersion();
    }

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();

    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final Set<Channel> clients = ConcurrentHashMap.newKeySet();
    @Getter(AccessLevel.NONE)
    private final Set<Class<?>> ignoredPackets = Collections.newSetFromMap(new IdentityHashMap<>());
    private Channel server;
    private final Set<Channel> connections = ConcurrentHashMap.newKeySet();
    private int maxClients = 0;
    private InetSocketAddress targetAddress;
    private Configuration configuration;
    private Path baseDir;
    private Path sessionsDir;
    private Path dataDir;
    private DefinitionRegistry<BlockDefinition> blockDefinitions;
    private DefinitionRegistry<BlockDefinition> blockDefinitionsHashed;

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        ProxyPass proxy = new ProxyPass();

        try (proxy) {
            Runtime.getRuntime().addShutdownHook(new Thread(proxy::shutdown, "ProxyPass shutdown"));
            proxy.boot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void boot() throws IOException {
        log.info("Loading configuration...");
        Path configPath = Paths.get(".").resolve("config.yml");

        if (Files.notExists(configPath) || !Files.isRegularFile(configPath)) {
            try (InputStream input = Objects.requireNonNull(ProxyPass.class.getClassLoader().getResourceAsStream("config.yml"), "config.yml")) {
                Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        configuration = Configuration.load(configPath);
        targetAddress = configuration.getDestination().getAddress();
        maxClients = configuration.getMaxClients();

        configuration.getIgnoredPackets().forEach(s -> {
            try {
                ignoredPackets.add(Class.forName("org.cloudburstmc.protocol.bedrock.packet." + s));
            } catch (ClassNotFoundException e) {
                log.warn("No packet with name {}", s);
            }
        });

        baseDir = Paths.get(".").toAbsolutePath();
        sessionsDir = baseDir.resolve("sessions");
        dataDir = baseDir.resolve("data");
        Files.createDirectories(sessionsDir);
        Files.createDirectories(dataDir);

        // Load block palette, if it exists
        Object object = this.loadGzipNBT("block_palette.nbt");

        if (object instanceof NbtMap map) {
            this.blockDefinitions = new NbtBlockDefinitionRegistry(map.getList("blocks", NbtType.COMPOUND), false);
            this.blockDefinitionsHashed = new NbtBlockDefinitionRegistry(map.getList("blocks", NbtType.COMPOUND), true);
        } else {
            this.blockDefinitions = this.blockDefinitionsHashed = new UnknownBlockDefinitionRegistry();
            log.warn("Failed to load block palette. Blocks will appear as runtime IDs in packet traces and creative_content.json!");
        }

        this.bindListener(this.configuration.getProxy());

        loop();
    }

    private void bindListener(Configuration.Address listener) throws IOException {
        ServerBootstrap bootstrap = new ServerBootstrap().group(this.eventLoopGroup);
        if (this.configuration.getTransport() == Transport.RAKNET) {
            bootstrap.channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                    .option(RakChannelOption.RAK_ADVERTISEMENT, ADVERTISEMENT.ipv4Port(listener.getPort())
                            .ipv6Port(listener.getPort()).toByteBuf());
        } else {
            try {
                Configuration.NetherNet settings = this.configuration.getNethernet();
                OperatorIdentity identity = OperatorIdentity.fromPemOrCreate(
                        this.baseDir.resolve(settings.getIdentityFile()).toFile(), "ProxyPass");

                NetherNetHTTPServerSignaling.Builder signaling = new NetherNetHTTPServerSignaling.Builder()
                        .setIdentity(identity)
                        .setTokenTrust(settings.isVerifyClientAuthentication() ? TokenTrust.MINECRAFT_AUTH : TokenTrust.ANY)
                        .setMotd(new PongData.Builder().setServerName("ProxyPass").setProtocol(PROTOCOL_VERSION)
                                .setVersion(MINECRAFT_VERSION).setMaxPlayerCount(this.maxClients == 0 ? 20 : this.maxClients)
                                .setOnlineAuth(settings.isVerifyClientAuthentication())
                                .setSelfSignedAuth(!settings.isVerifyClientAuthentication()).build());

                if (settings.getTlsCertificate() != null) {
                    signaling.setHttpsPem(this.baseDir.resolve(settings.getTlsCertificate()).toFile(),
                            this.baseDir.resolve(settings.getTlsPrivateKey()).toFile());
                }

                bootstrap.channelFactory(NetherNetChannelFactory.server(signaling.build()))
                        .option(NetherChannelOption.NETHER_SERVER_RTC_HANDSHAKE_TIMEOUT_SECONDS, 30);
            } catch (Exception failure) {
                throw new IOException("Unable to configure the NetherNet listener", failure);
            }
        }

        bootstrap.childHandler(new ProxyChannelInitializer<>(this, this.configuration.getTransport(), true,
                (peer, id) -> new ProxyServerSession(peer, id, this),
                session -> session.setPacketHandler(new UpstreamPacketHandler(session, this))));
        ChannelFuture bound = bootstrap.bind(listener.getAddress()).awaitUninterruptibly();
        if (!bound.isSuccess()) {
            bound.channel().close();
            throw new IOException("Unable to bind " + listener.getAddress(), bound.cause());
        }

        this.server = bound.channel();
        log.info("{} listener started on {}", this.configuration.getTransport(), listener.getAddress());
    }

    public CompletableFuture<ProxyClientSession> newClient(KeyPair keyPair, ChainValidationResult.IdentityData identity, Consumer<ProxyClientSession> sessionConsumer) {
        CompletableFuture<ProxyClientSession> result = new CompletableFuture<>();
        Configuration.Address destination = this.configuration.getDestination();
        log.info("Connecting {} to backend {} using {}", identity.displayName, this.targetAddress, this.configuration.getTransport());

        Bootstrap bootstrap = new Bootstrap().group(this.eventLoopGroup);
        try {
            if (this.configuration.getTransport() == Transport.RAKNET) {
                bootstrap.channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                        .option(RakChannelOption.RAK_PROTOCOL_VERSION, CODEC.getRaknetProtocolVersion());
            } else {
                OperatorIdentity assertion = new OperatorIdentity(keyPair.getPrivate(), keyPair.getPublic(), null, "ProxyPass")
                        .forPlayer(identity.xuid, identity.displayName);
                bootstrap.channelFactory(NetherNetChannelFactory.client(() -> new NetherNetHTTPClientSignaling(destination.signalingSettings())))
                        .option(NetherChannelOption.NETHER_CLIENT_IDENTITY, assertion)
                        .option(NetherChannelOption.NETHER_CLIENT_HANDSHAKE_TIMEOUT_MS, 30000);
                if (destination.getServerPublicKey() != null) {
                    bootstrap.option(NetherChannelOption.NETHER_CLIENT_SERVER_TRUST, TokenTrust.pinnedTo(EncryptionUtils.parseKey(destination.getServerPublicKey())));
                }
            }

            bootstrap.handler(new ProxyChannelInitializer<>(this, this.configuration.getTransport(), false,
                    (peer, id) -> new ProxyClientSession(peer, id, this), session -> {
                sessionConsumer.accept(session);
                result.complete(session);
            }));

            ChannelFuture connection = bootstrap.connect(this.targetAddress);
            connection.addListener(future -> {
                if (!future.isSuccess()) {
                    connection.channel().close();
                    result.completeExceptionally(future.cause());
                }
            });

            connection.channel().closeFuture().addListener(ignored -> result.completeExceptionally(new IOException("Backend connection closed before login initialization")));
        } catch (Exception failure) {
            result.completeExceptionally(failure);
        }

        return result;
    }

    public synchronized boolean registerConnection(Channel channel, boolean incoming) {
        if (!this.running.get() || incoming && this.isFull()) {
            return false;
        }

        this.connections.add(channel);
        if (incoming) {
            this.clients.add(channel);
        }

        channel.closeFuture().addListener(ignored -> {
            this.connections.remove(channel);
            this.clients.remove(channel);
        });

        return true;
    }

    private void loop() {
        while (running.get()) {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.shutdown();
            }
        }
    }

    @Override
    public void close() {
        this.shutdown();

        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        if (this.server != null) {
            this.server.close().awaitUninterruptibly();
        }

        List.copyOf(this.connections).forEach(channel -> channel.close().awaitUninterruptibly());
        this.eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
        SessionLogger.shutdown();
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            synchronized (this) {
                this.notify();
            }
        }
    }

    public void saveCompressedNBT(String dataName, Object dataTag) {
        Path path = dataDir.resolve(dataName + ".nbt");
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             NBTOutputStream nbtOutputStream = NbtUtils.createGZIPWriter(outputStream)) {
            nbtOutputStream.writeTag(dataTag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveNBT(String dataName, Object dataTag) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             NBTOutputStream nbtOutputStream = NbtUtils.createNetworkWriter(outputStream)) {
            nbtOutputStream.writeTag(dataTag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Object loadNBT(String dataName) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (InputStream inputStream = Files.newInputStream(path);
             NBTInputStream nbtInputStream = NbtUtils.createNetworkReader(inputStream)) {
            return nbtInputStream.readTag();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Object loadGzipNBT(String dataName) {
        Path path = dataDir.resolve(dataName);
        try (InputStream inputStream = Files.newInputStream(path);
             NBTInputStream nbtInputStream = NbtUtils.createGZIPReader(inputStream)) {
            return nbtInputStream.readTag();
        } catch (IOException e) {
            return null;
        }
    }

    public void saveJson(String name, Object object) {
        Path outPath = dataDir.resolve(name);
        try (OutputStream outputStream = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            ProxyPass.JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputStream, object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T loadJson(String name, TypeReference<T> reference) {
        Path path = dataDir.resolve(name);
        try (InputStream inputStream = Files.newInputStream(path, StandardOpenOption.READ)) {
            return ProxyPass.JSON_MAPPER.readValue(inputStream, reference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveMojangson(String name, NbtMap nbt) {
        Path outPath = dataDir.resolve(name);
        try {
            Files.writeString(outPath, nbt.toString(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void savePacket(BedrockPacketWrapper wrapper) {
        String name = wrapper.getPacket().getPacketType().getName().toLowerCase() + "_" + System.currentTimeMillis() + ".dat";

        ByteBuf packetBuf = wrapper.getPacketBuffer().slice();
        packetBuf.skipBytes(wrapper.getHeaderLength()); // skip header

        ByteBuf buffer = packetBuf.alloc().ioBuffer();
        buffer.writeInt(wrapper.getPacketId()); // packet ID
        buffer.writeBytes(packetBuf); // packet data

        Path outPath = dataDir.resolve(name);
        try (OutputStream outputStream = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            byte[] bytes = new byte[1024 * 8];
            while (buffer.isReadable()) {
                int read = Math.min(buffer.readableBytes(), bytes.length);
                buffer.readBytes(bytes, 0, read);
                outputStream.write(bytes, 0, read);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            buffer.release();
        }
    }

    public boolean isIgnoredPacket(Class<?> clazz) {
        return this.ignoredPackets.contains(clazz);
    }

    public boolean isFull() {
        return maxClients > 0 && this.clients.size() >= maxClients;
    }
}
