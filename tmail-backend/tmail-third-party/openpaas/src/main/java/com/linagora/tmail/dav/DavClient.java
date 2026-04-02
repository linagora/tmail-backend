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

package com.linagora.tmail.dav;

import java.util.function.UnaryOperator;

import javax.net.ssl.SSLException;

import jakarta.inject.Inject;

import org.apache.james.core.Username;

import com.linagora.tmail.configuration.DavConfiguration;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;

public class DavClient {
    private static final String ACCEPT_VCARD_JSON = "application/vcard+json";

    private final HttpClient httpClient;
    private final DavConfiguration config;

    @Inject
    public DavClient(DavConfiguration config) throws SSLException {
        httpClient = createHttpClient(config);
        this.config = config;
    }

    public CalDavClient caldav(Username username) {
        return new CalDavClient(httpClient.headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username.asString()))));
    }

    public CardDavClient carddav(Username username) {
        return new CardDavClient(httpClient.headers(headers -> cardDavHeaders(username).apply(headers)));
    }

    private UnaryOperator<HttpHeaders> cardDavHeaders(Username username) {
        return headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_VCARD_JSON)
            .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username.asString()));
    }

    private static HttpClient createHttpClient(DavConfiguration config) throws SSLException {
        HttpClient client = HttpClient.create()
            .baseUrl(config.baseUrl().toString())
            .responseTimeout(config.responseTimeout());
        if (config.trustAllSslCerts()) {
            SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            return client.secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        }
        return client;
    }
}
