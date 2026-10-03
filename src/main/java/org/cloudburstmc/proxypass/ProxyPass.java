package org.cloudburstmc.proxypass;

import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
import lombok.Setter;
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
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.cloudburstmc.proxypass.auth.Account;
import org.cloudburstmc.proxypass.auth.AuthData;
import org.cloudburstmc.proxypass.auth.AuthHandler;
import org.cloudburstmc.proxypass.network.Transport;
import org.cloudburstmc.proxypass.network.bedrock.ProxyChannelInitializer;
import org.cloudburstmc.proxypass.network.bedrock.jackson.*;
import org.cloudburstmc.proxypass.network.bedrock.session.*;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry.NbtBlockDefinition;
import org.cloudburstmc.proxypass.network.bedrock.util.UnknownBlockDefinitionRegistry;
import org.cloudburstmc.proxypass.ui.PacketLoggingWindow;
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

import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
@Getter
public class ProxyPass implements AutoCloseable {
    public static final ObjectMapper JSON_MAPPER;
    public static final YAMLMapper YAML_MAPPER;

    private static final SimpleModule MODULE = new SimpleModule("ProxyPass", Version.unknownVersion())
            .addSerializer(Color.class, new ColorSerializer())
            .addDeserializer(Color.class, new ColorDeserializer())
            .addSerializer(NbtBlockDefinition.class, new NbtDefinitionSerializer())
            .addSerializer(OptionalBoolean.class, new OptionalBooleanSerializer())
            .addSerializer(ByteBuf.class, new ByteBufSerializer());

    public static final String MINECRAFT_VERSION;

    public static final BedrockCodec BASE_CODEC = Bedrock_v2193.CODEC;

    public static final BedrockCodec CODEC = BASE_CODEC.toBuilder()
            .helper(() -> {
                BedrockCodecHelper helper = BASE_CODEC.createHelper();
                helper.setEncodingSettings(EncodingSettings.UNLIMITED);
                return helper;
            }).build();
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

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ScheduledExecutorService logExecutor = Executors.newSingleThreadScheduledExecutor();
    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final Set<Channel> clients = ConcurrentHashMap.newKeySet();
    private Channel server;
    private final Set<Channel> connections = ConcurrentHashMap.newKeySet();
    private int maxClients = 0;
    private InetSocketAddress targetAddress;
    private volatile Configuration configuration;
    private Path baseDir;
    private Path sessionsDir;
    private Path dataDir;
    private DefinitionRegistry<BlockDefinition> blockDefinitions;
    private DefinitionRegistry<BlockDefinition> blockDefinitionsHashed;
    private Account currentAccount;
    @Setter
    private Consumer<ProxyPlayerSession> sessionInitHandler = (ignored) -> {};

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        if (List.of(args).contains("ui")) {
            SwingUtilities.invokeLater(() -> {
                FlatArcDarkIJTheme.setup();
                try {
                    new PacketLoggingWindow();
                } catch (IOException failure) {
                    log.error("Unable to open the packet inspector", failure);
                }
            });
            return;
        }

        Path configPath = Paths.get("config.yml");
        try {
            if (Files.notExists(configPath)) {
                try (InputStream input = Objects.requireNonNull(ProxyPass.class.getClassLoader().getResourceAsStream("config.yml"), "config.yml")) {
                    Files.copy(input, configPath);
                }
            }
            Configuration config = Configuration.load(configPath);
            Account account = AuthHandler.authenticateCli(config);
            try (ProxyPass proxy = new ProxyPass()) {
                Runtime.getRuntime().addShutdownHook(new Thread(proxy::close, "ProxyPass shutdown"));
                proxy.start(config, account);
                proxy.getServer().closeFuture().syncUninterruptibly();
            }
        } catch (Exception failure) {
            throw new RuntimeException("Unable to start the proxy", failure);
        }
    }

    public void start(Configuration configuration, @Nullable Account currentAccount) throws IOException {
        if (this.closed.get() || this.running.get()) {
            throw new IllegalStateException("Proxy instance cannot be started again");
        }
        configuration.validate();
        this.configuration = configuration;
        this.currentAccount = currentAccount;
        if (this.currentAccount != null) {
            log.info("Authenticated as {}.", AuthHandler.getAccountName(this.currentAccount.toJson()));
        } else {
            log.info("Not authenticated.");
        }
        targetAddress = configuration.getDestination().getAddress();
        maxClients = configuration.getMaxClients();

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

        this.running.set(true);
        try {
            this.bindListener(this.configuration.getProxy());
        } catch (IOException | RuntimeException failure) {
            this.close();
            throw failure;
        }
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
                session -> session.setPacketHandler(new UpstreamPacketHandler(session, this, this.currentAccount))));
        ChannelFuture bound = bootstrap.bind(listener.getAddress()).awaitUninterruptibly();
        if (!bound.isSuccess()) {
            bound.channel().close();
            throw new IOException("Unable to bind " + listener.getAddress(), bound.cause());
        }

        this.server = bound.channel();
        log.info("{} listener started on {}", this.configuration.getTransport(), listener.getAddress());
    }

    public CompletableFuture<ProxyClientSession> newClient(KeyPair keyPair, AuthData identity, Consumer<ProxyClientSession> sessionConsumer) {
        CompletableFuture<ProxyClientSession> result = new CompletableFuture<>();
        Configuration.Address destination = this.configuration.getDestination();
        log.info("Connecting {} to backend {} using {}", identity.getDisplayName(), this.targetAddress, this.configuration.getTransport());

        Bootstrap bootstrap = new Bootstrap().group(this.eventLoopGroup);
        try {
            if (this.configuration.getTransport() == Transport.RAKNET) {
                bootstrap.channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                        .option(RakChannelOption.RAK_PROTOCOL_VERSION, CODEC.getRaknetProtocolVersion());
            } else {
                OperatorIdentity assertion = this.currentAccount == null
                        ? new OperatorIdentity(keyPair.getPrivate(), keyPair.getPublic(), null, "ProxyPass")
                                .forPlayer(identity.getXuid(), identity.getDisplayName())
                        : OperatorIdentity.fromToken(keyPair,
                                this.currentAccount.authManager().getMinecraftMultiplayerToken().getCached().getToken(),
                                "https://authorization.franchise.minecraft-services.net/");
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

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        this.running.set(false);
        if (this.server != null) {
            this.server.close().awaitUninterruptibly();
        }
        List.copyOf(this.connections).forEach(channel -> channel.close().awaitUninterruptibly());
        this.eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
        this.logExecutor.shutdown();
    }

    public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.runAsync(this::close);
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

    public void resetConfig(Configuration configuration) {
        this.configuration = configuration;
    }

    public boolean isIgnoredPacket(Class<?> clazz) {
        return this.configuration.getIgnoredPackets().contains(clazz.getSimpleName());
    }

    public boolean isBlockedPacket(Class<?> clazz) {
        return this.configuration.getBlockedPackets().contains(clazz.getSimpleName());
    }

    public void setBlockedPackets(Set<Class<? extends BedrockPacket>> blockedPackets) {
        this.configuration.setBlockedPackets(blockedPackets.stream().map(Class::getSimpleName).collect(Collectors.toSet()));
    }

    public boolean isFull() {
        return maxClients > 0 && this.clients.size() >= maxClients;
    }
}
