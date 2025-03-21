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

import java.util.function.Consumer;

import jakarta.inject.Inject;

import org.apache.james.jmap.http.Authenticator;

import com.linagora.calendar.restapi.routes.JwtSigner;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
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
        return request.receive().aggregate().asByteArray()
            .switchIfEmpty(Mono.just("".getBytes()))
            .flatMap(payload -> handleAuthIfNeeded(request)
                .flatMap(headerTransformation -> client.headers(headers -> {
                        headerTransformation.accept(headers);
                        headers.add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    })
                    .request(request.method())
                    .uri(configuration.getOpenpaasBackendURL().toString() + request.uri())
                    .send((req, out) -> out.sendByteArray(Mono.just(payload)))
                    .response((res, in) -> {
                        response.status(res.status());
                        response.headers(res.responseHeaders());
                        return response.sendByteArray(in.asByteArray());
                    })
                    .then()));
    }

    private Mono<Consumer<HttpHeaders>> handleAuthIfNeeded(HttpServerRequest request) {
        if (request.requestHeaders().contains(HttpHeaderNames.AUTHORIZATION)) {
            return authenticator.authenticate(request)
                .flatMap(session -> Mono.fromCallable(() -> jwtSigner.generate(session.getUser().asString())).subscribeOn(Schedulers.parallel()))
                .map(token -> headers -> headers.add(HttpHeaderNames.AUTHORIZATION, "Bearer " + token));
        }
        return Mono.just(headers -> {

        });
    }
}
