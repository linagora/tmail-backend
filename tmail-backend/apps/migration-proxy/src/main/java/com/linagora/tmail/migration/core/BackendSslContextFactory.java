/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.migration.core;

import java.util.Optional;

import jakarta.inject.Inject;

import com.github.fge.lambdas.Throwing;

import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

/**
 * Builds the client-side {@link SslContext} the proxy uses to reach a backend over implicit TLS.
 *
 * <p>Two flavours are cached: one that validates the backend certificate against the system trust
 * store, and one that trusts any certificate (for self-signed certs during a migration), selected
 * per backend through {@link Backend#sslIgnoreCertificates()}.
 *
 * <p>The TLS provider follows the same {@code james.tcnative.enabled} toggle as the listening side:
 * BoringSSL/OpenSSL via netty-tcnative when enabled and available, the JDK provider (nio) otherwise.
 */
public class BackendSslContextFactory {
    private final SslProvider sslProvider;
    private volatile SslContext validating;
    private volatile SslContext trustAll;

    @Inject
    public BackendSslContextFactory() {
        this.sslProvider = resolveProvider();
    }

    public Optional<SslContext> forBackend(Backend backend) {
        if (!backend.ssl()) {
            return Optional.empty();
        }
        return Optional.of(backend.sslIgnoreCertificates() ? trustAllContext() : validatingContext());
    }

    public SslProvider sslProvider() {
        return sslProvider;
    }

    private SslContext validatingContext() {
        if (validating == null) {
            synchronized (this) {
                if (validating == null) {
                    validating = Throwing.supplier(() -> SslContextBuilder.forClient()
                        .sslProvider(sslProvider)
                        .build()).get();
                }
            }
        }
        return validating;
    }

    private SslContext trustAllContext() {
        if (trustAll == null) {
            synchronized (this) {
                if (trustAll == null) {
                    trustAll = Throwing.supplier(() -> SslContextBuilder.forClient()
                        .sslProvider(sslProvider)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build()).get();
                }
            }
        }
        return trustAll;
    }

    private static SslProvider resolveProvider() {
        if (Boolean.getBoolean("james.tcnative.enabled") && OpenSsl.isAvailable()) {
            return SslProvider.OPENSSL;
        }
        return SslProvider.JDK;
    }
}
