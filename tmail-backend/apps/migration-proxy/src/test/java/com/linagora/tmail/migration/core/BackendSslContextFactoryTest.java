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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.util.Host;
import org.junit.jupiter.api.Test;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.SslContext;

class BackendSslContextFactoryTest {
    private static final Backend VALIDATING_BACKEND = new Backend("old", Host.from("imap.example.com", 993),
        true, false, false, Optional.empty());

    @Test
    void validatingContextShouldEnableHostnameVerification() {
        SslContext sslContext = new BackendSslContextFactory().forBackend(VALIDATING_BACKEND).orElseThrow();

        assertThat(sslContext.newEngine(UnpooledByteBufAllocator.DEFAULT,
                VALIDATING_BACKEND.host().getHostName(), VALIDATING_BACKEND.host().getPort())
            .getSSLParameters()
            .getEndpointIdentificationAlgorithm())
            .isEqualTo("HTTPS");
    }
}
