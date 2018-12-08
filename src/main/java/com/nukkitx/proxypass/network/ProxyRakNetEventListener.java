package com.nukkitx.proxypass.network;

import com.nukkitx.network.raknet.RakNetServerEventListener;
import com.nukkitx.proxypass.ProxyPass;

import javax.annotation.Nonnull;
import java.net.InetSocketAddress;

public class ProxyRakNetEventListener implements RakNetServerEventListener {
    private final Advertisement advertisement = new Advertisement("MCPE", "ProxyPass",
            ProxyPass.PROTOCOL_VERSION, ProxyPass.MINECRAFT_VERSION, 0,
            1, "https://github.com/NukkitX/ProxyPass", "SMP");

    @Nonnull
    @Override
    public Action onConnectionRequest(InetSocketAddress address, int protocolVersion) {
        return Action.CONTINUE;
    }

    @Nonnull
    public Advertisement onQuery(InetSocketAddress address) {
        return advertisement;
    }
}
