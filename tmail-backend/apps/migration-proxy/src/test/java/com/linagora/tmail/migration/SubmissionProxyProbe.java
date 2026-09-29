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

import java.net.InetSocketAddress;

import jakarta.inject.Inject;

import org.apache.james.utils.GuiceProbe;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.migration.submission.SubmissionProxyServerFactory;

/**
 * Exposes the ephemeral ports of the submission listeners declared in the test
 * {@code submissionproxy.xml}: STARTTLS, implicit TLS, then clear text behind a PROXY protocol header.
 */
class SubmissionProxyProbe implements GuiceProbe {
    static final Module MODULE = binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
        .addBinding()
        .to(SubmissionProxyProbe.class);

    private static final int STARTTLS_LISTENER = 0;
    private static final int IMPLICIT_TLS_LISTENER = 1;
    private static final int HAPROXY_LISTENER = 2;

    private final SubmissionProxyServerFactory serverFactory;

    @Inject
    SubmissionProxyProbe(SubmissionProxyServerFactory serverFactory) {
        this.serverFactory = serverFactory;
    }

    int startTlsPort() {
        return port(STARTTLS_LISTENER);
    }

    int implicitTlsPort() {
        return port(IMPLICIT_TLS_LISTENER);
    }

    int haproxyPort() {
        return port(HAPROXY_LISTENER);
    }

    private int port(int listener) {
        return serverFactory.getServers().get(listener).getListenAddresses().stream()
            .findFirst()
            .map(InetSocketAddress::getPort)
            .orElseThrow(() -> new IllegalStateException("Submission listener " + listener + " is not bound"));
    }
}
