package com.nukkitx.proxypass.network;

import com.nukkitx.network.raknet.RakNetServerEventListener;
import com.nukkitx.proxypass.ProxyPass;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;

@Log4j2
public class ProxyRakNetEventListener implements RakNetServerEventListener {
    private final Advertisement advertisement = new Advertisement("MCPE", "ProxyPass",
            ProxyPass.PROTOCOL_VERSION, ProxyPass.MINECRAFT_VERSION, 0,
            1, "https://github.com/NukkitX/ProxyPass", "SMP");

    @Nonnull
    @Override
    public Action onConnectionRequest(InetSocketAddress address, int protocolVersion) {
        log.trace("RakNet version: {}", protocolVersion);
        return Action.CONTINUE;
    }

    @Nonnull
    public Advertisement onQuery(InetSocketAddress address) {
        return advertisement;
    }
}
