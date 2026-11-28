package org.cloudburstmc.proxypass.network;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import javax.net.ssl.*;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Trusts what the JDK trust store trusts, without matching the host name. Some servers serve a
 * certificate for another of their names.
 */
@UtilityClass
public class ChainOnlyTrust {

    public final SSLContext CONTEXT = create();

    private SSLContext create() {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init((KeyStore) null);
            X509TrustManager delegate = null;
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509TrustManager x509) {
                    delegate = x509;
                    break;
                }
            }

            if (delegate == null) {
                throw new IllegalStateException("The JDK offers no X.509 trust manager");
            }

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{new Manager(delegate)}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to build the TLS context for signaling", e);
        }
    }

    /**
     * Every check goes to the delegate's two-argument form, which does not match the host name.
     */
    @RequiredArgsConstructor
    private final class Manager extends X509ExtendedTrustManager {
        private final X509TrustManager delegate;

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            this.delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            this.delegate.checkServerTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            this.delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            this.delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return this.delegate.getAcceptedIssuers();
        }
    }
}
