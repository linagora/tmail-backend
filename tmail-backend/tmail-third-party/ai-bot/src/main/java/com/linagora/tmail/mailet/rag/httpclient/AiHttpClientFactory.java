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
package com.linagora.tmail.mailet.rag.httpclient;

import javax.net.ssl.SSLException;

import com.linagora.tmail.mailet.conf.AiHttpClientConfiguration;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;


public final class AiHttpClientFactory {

    public static HttpClient create(AiHttpClientConfiguration configuration) {
        HttpClient client = HttpClient.create()
            .baseUrl(configuration.getBaseURLOpt().orElseThrow().toString())
            .headers(headers -> headers
                .add("Authorization", "Bearer " + configuration.getAuthorizationToken())
                .add("Accept", "application/json"));

        return configuration.getTrustAllCertificates()
            ? client.secure(spec -> spec.sslContext(buildInsecureSslContext()))
            : client.secure();
    }

    public static SslContext buildInsecureSslContext() {
        try {
            return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        } catch (SSLException e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }
}
