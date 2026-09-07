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

package com.linagora.tmail.migration;

import java.security.cert.CertificateException;
import java.util.Optional;

import javax.net.ssl.SSLException;

import com.linagora.tmail.migration.core.Backend;
import com.linagora.tmail.migration.core.BackendSslContextFactory;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

public class BackendTlsTestFixture implements AutoCloseable {
    public static final String HOSTNAME = "localhost";

    private final SelfSignedCertificate certificate;
    private final SslContext serverContext;
    private final SslContext clientContext;

    public BackendTlsTestFixture() throws CertificateException, SSLException {
        certificate = new SelfSignedCertificate(HOSTNAME);
        serverContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();
        clientContext = SslContextBuilder.forClient()
            .trustManager(certificate.certificate())
            .endpointIdentificationAlgorithm("HTTPS")
            .build();
    }

    public StubBackendServer server(String greeting) {
        return new StubBackendServer(greeting, serverContext);
    }

    public SslContext clientContext() {
        return clientContext;
    }

    public BackendSslContextFactory sslContextFactory() {
        return new BackendSslContextFactory() {
            @Override
            public Optional<SslContext> forBackend(Backend backend) {
                return backend.ssl() ? Optional.of(clientContext) : Optional.empty();
            }
        };
    }

    @Override
    public void close() {
        certificate.delete();
    }
}
