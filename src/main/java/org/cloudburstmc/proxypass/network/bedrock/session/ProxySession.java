package org.cloudburstmc.proxypass.network.bedrock.session;

import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.proxypass.ProxyPass;

public interface ProxySession {
    ProxyPass getProxyPass();

    BedrockSession getSendSession();

    void setSendSession(BedrockSession session);
}
