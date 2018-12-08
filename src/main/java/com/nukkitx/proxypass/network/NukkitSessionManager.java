package com.nukkitx.proxypass.network;

import com.flowpowered.math.GenericMath;
import com.nukkitx.network.SessionManager;
import com.nukkitx.protocol.bedrock.session.BedrockSession;
import com.nukkitx.proxypass.network.bedrock.session.ProxyPlayerSession;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ThreadPoolExecutor;

@Log4j2
public class NukkitSessionManager extends SessionManager<BedrockSession<ProxyPlayerSession>> {
    private static final int SESSIONS_PER_THREAD = 50;

    /*public NukkitSessionManager() {
        super(Executors.newSingleThreadScheduledExecutor());
        ScheduledExecutorService service = (ScheduledExecutorService) executor;
        service.scheduleAtFixedRate(this::onTick, 50, 50, TimeUnit.MILLISECONDS);
    }*/

    @Override
    protected void onAddSession(BedrockSession<ProxyPlayerSession> session) {
        adjustPoolSize();
    }

    @Override
    protected void onRemoveSession(BedrockSession<ProxyPlayerSession> session) {
        adjustPoolSize();
    }

    private void adjustPoolSize() {
        if (executor instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor sessionTicker = (ThreadPoolExecutor) executor;
            int threads = GenericMath.clamp(sessions.size() / SESSIONS_PER_THREAD, 1, Runtime.getRuntime().availableProcessors());
            if (sessionTicker.getMaximumPoolSize() != threads) {
                sessionTicker.setMaximumPoolSize(threads);
            }
        }
    }

    public void onTick() {
        for (BedrockSession session : sessions.values()) {
            executor.execute(session::onTick);
        }
    }
}
