package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.logging.SessionLogger;

import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicBoolean;

@Log4j2
@Getter
public class ProxyPlayerSession {

    private final ProxyServerSession upstream;
    private final ProxyClientSession downstream;
    private final ProxyPass proxy;
    private final ChainValidationResult.IdentityData identityData;
    private final long timestamp = System.currentTimeMillis();

    @Getter(AccessLevel.PACKAGE)
    private final KeyPair proxyKeyPair;
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean closed = new AtomicBoolean();

    public final SessionLogger logger;

    public ProxyPlayerSession(ProxyServerSession upstream, ProxyClientSession downstream, ProxyPass proxy, ChainValidationResult.IdentityData identityData, KeyPair keyPair) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.proxy = proxy;
        this.identityData = identityData;
        this.proxyKeyPair = keyPair;
        this.logger = new SessionLogger(
                proxy,
                proxy.getSessionsDir(),
                this.identityData.displayName,
                timestamp
        );
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
