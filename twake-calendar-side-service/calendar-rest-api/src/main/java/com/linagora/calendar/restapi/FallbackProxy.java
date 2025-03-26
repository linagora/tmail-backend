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
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.calendar.restapi.routes.JwtSigner;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class FallbackProxy {
    public static final Logger LOGGER = LoggerFactory.getLogger(FallbackProxy.class);
    private final HttpClient client;
    private final Authenticator authenticator;
    private final RestApiConfiguration configuration;
    private final JwtSigner jwtSigner;
    private final MetricFactory metricFactory;

    @Inject
    public FallbackProxy(Authenticator authenticator, RestApiConfiguration configuration, JwtSigner jwtSigner, MetricFactory metricFactory) {
        this.authenticator = authenticator;
        this.configuration = configuration;
        this.jwtSigner = jwtSigner;
        this.metricFactory = metricFactory;
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
        LOGGER.warn("Proxying {} {}", request.method(), request.uri());
        return request.receive().aggregate().asByteArray()
            .switchIfEmpty(Mono.just("".getBytes()))
            .flatMap(payload -> handleAuthIfNeeded(request)
                .flatMap(headerTransformation ->
                    Mono.from(metricFactory.decoratePublisherWithTimerMetric("fallbackProxy",
                            client.headers(headers -> {
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
                                })))
                        .then()));
    }

    private Mono<Consumer<HttpHeaders>> handleAuthIfNeeded(HttpServerRequest request) {
        if (request.requestHeaders().contains(HttpHeaderNames.AUTHORIZATION)) {
            return authenticator.authenticate(request)
                .flatMap(session -> jwtSigner.generate(session.getUser().asString()))
                .map(token -> headers -> headers.add(HttpHeaderNames.AUTHORIZATION, "Bearer " + token));
        }
        return Mono.just(headers -> {

        });
    }
}
