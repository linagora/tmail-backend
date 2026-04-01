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

import javax.net.ssl.SSLException;

import jakarta.inject.Inject;

import com.linagora.tmail.configuration.DavConfiguration;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.netty.http.client.HttpClient;

public class DavClient {
    private final CalDavClient calDav;
    private final CardDavClient cardDav;

    @Inject
    public DavClient(DavConfiguration config) throws SSLException {
        HttpClient httpClient = createHttpClient(config);
        this.calDav = new CalDavClient(httpClient, config);
        this.cardDav = new CardDavClient(httpClient, config);
    }

    public CalDavClient caldav() {
        return calDav;
    }

    public CardDavClient carddav() {
        return cardDav;
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
