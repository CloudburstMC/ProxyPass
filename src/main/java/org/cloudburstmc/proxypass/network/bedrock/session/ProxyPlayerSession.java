package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.auth.AuthData;
import org.cloudburstmc.proxypass.network.bedrock.logging.SessionLogger;
import org.cloudburstmc.proxypass.ui.UIPacketData;

import java.security.KeyPair;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
@Getter
public class ProxyPlayerSession {

    private final ProxyServerSession upstream;
    private final ProxyClientSession downstream;
    private final ProxyPass proxy;
    private final ChainValidationResult.IdentityData identityData;
    private final AuthData authData;
    private final long timestamp = System.currentTimeMillis();

    @Getter(AccessLevel.PACKAGE)
    private final KeyPair proxyKeyPair;

    @Getter(AccessLevel.NONE)
    private final AtomicBoolean closed = new AtomicBoolean();

    public final SessionLogger logger;

    @Setter
    public BiConsumer<BedrockPacket, Boolean> packetHandler = (packet, upstream) -> {};
    @Setter
    private BiConsumer<BedrockPacketWrapper, UIPacketData.Direction> extraLogHandler = (ignored1, ignored2) -> {};
    @Setter
    private Runnable onClose = () -> {};

    public ProxyPlayerSession(ProxyServerSession upstream, ProxyClientSession downstream, ProxyPass proxy, AuthData authData, ChainValidationResult.IdentityData identityData, KeyPair proxyKeyPair) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.proxy = proxy;
        this.authData = authData;
        this.identityData = identityData;
        this.proxyKeyPair = proxyKeyPair;
//        this.upstream.addDisconnectHandler(reason -> {
//            if (reason != DisconnectReason.DISCONNECTED) {
//                this.downstream.disconnect();
//            }
//        });
        this.upstream.setOnClose(() -> this.onClose.run());
        this.logger = new SessionLogger(
                this,
                proxy,
                proxy.getSessionsDir(),
                this.authData.getDisplayName(),
                timestamp
        );
        proxy.getSessionInitHandler().accept(this);
        logger.start();
    }

    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.upstream.getPeer().getChannel().close();
            this.downstream.getPeer().getChannel().close();
            this.logger.close();
        }
    }
}
