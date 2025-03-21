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

package com.linagora.calendar.restapi;

import jakarta.inject.Inject;

import org.apache.james.jmap.http.Authenticator;

import com.linagora.calendar.restapi.routes.JwtSigner;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class FallbackProxy {
    private final HttpClient client;
    private final Authenticator authenticator;
    private final RestApiConfiguration configuration;
    private final JwtSigner jwtSigner;

    @Inject
    public FallbackProxy(Authenticator authenticator, RestApiConfiguration configuration, JwtSigner jwtSigner) {
        this.authenticator = authenticator;
        this.configuration = configuration;
        this.jwtSigner = jwtSigner;
        this.client = createClient();

    }

    private HttpClient createClient() {
        if (configuration.openpaasBackendTrustAllCerts()) {
                return HttpClient.create().secure(sslContextSpec -> sslContextSpec.sslContext(
                    SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)));
        }
        return HttpClient.create();
    }

    public Mono<Void> forwardRequest(HttpServerRequest request, HttpServerResponse response) {
        return authenticator.authenticate(request)
            .flatMap(session -> Mono.fromCallable(() -> jwtSigner.generate(session.getUser().asString())).subscribeOn(Schedulers.parallel()))
            .flatMap(token -> client.headers(headers -> headers.add(request.requestHeaders()))
                .request(request.method())
                .uri(configuration.getOpenpaasBackendURL().toString() + request.uri())
                .send((req, out) -> out.send(request.receive().retain()))
                .response((res, in) -> {
                    response.status(res.status());
                    response.addHeader(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
                    res.responseHeaders().forEach(entry -> {
                            if (!entry.getKey().equalsIgnoreCase("Authorization")) {
                                response.addHeader(entry.getKey(), entry.getValue());
                            }
                        });
                    return response.send(in.retain());
                })
                .then());
    }
}
