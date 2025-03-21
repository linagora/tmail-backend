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

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED;
import static reactor.netty.Metrics.HTTP_CLIENT_PREFIX;
import static reactor.netty.Metrics.URI;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.jmap.JMAPRoute;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.exceptions.UnauthorizedException;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;

public class CalendarRestApiServer implements Startable  {
    public static final boolean REACTOR_NETTY_METRICS_ENABLE = Boolean.parseBoolean(System.getProperty("james.jmap.reactor.netty.metrics.enabled", "false"));
    private static final int REACTOR_NETTY_METRICS_MAX_URI_TAGS = 100;
    private static final int RANDOM_PORT = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarRestApiServer.class);

    private final List<JMAPRoute> routes;
    private final RestApiConfiguration configuration;
    private final FallbackProxy fallbackProxy;
    private Optional<DisposableServer> server;

    @Inject
    public CalendarRestApiServer(Set<JMAPRoutes> jmapRoutes, FallbackProxy fallbackProxy, RestApiConfiguration configuration) {
        this.routes = jmapRoutes.stream().flatMap(JMAPRoutes::routes).collect(Collectors.toList());
        this.configuration = configuration;
        this.server = Optional.empty();
        this.fallbackProxy = fallbackProxy;
    }

    public void start() {
        server = Optional.of(HttpServer.create()
            .port(configuration.getPort()
                .map(Port::getValue)
                .orElse(RANDOM_PORT))
            .handle((request, response) -> Mono.from(handleVersionRoute(request)
                .handleRequest(request, response))
                .onErrorResume(e -> {
                    if (e instanceof IllegalArgumentException) {
                        LOGGER.info("Invalid request", e);
                        return response.status(BAD_REQUEST).send();
                    }
                    if (e instanceof UnauthorizedException) {
                        LOGGER.info("Wrong authentication", e);
                        return response.status(UNAUTHORIZED).send();
                    }

                    LOGGER.error("Unexpected error", e);
                    return response.status(INTERNAL_SERVER_ERROR).send();
                }))
            .wiretap(wireTapEnabled())
            .metrics(REACTOR_NETTY_METRICS_ENABLE, Function.identity())
            .bindNow());

        if (REACTOR_NETTY_METRICS_ENABLE) {
            configureReactorNettyMetrics();
        }
    }

    public Port getPort() {
        return server.map(DisposableServer::port)
            .map(Port::of)
            .orElseThrow(() -> new IllegalStateException("port is not available because server is not started or disabled"));
    }

    private JMAPRoute.Action handleVersionRoute(HttpServerRequest request) {
        try {
            return routes.stream()
                .filter(jmapRoute -> jmapRoute.matches(request))
                .map(JMAPRoute::getAction)
                .findFirst()
                .orElse(fallbackProxy::forwardRequest);
        } catch (IllegalArgumentException e) {
            return (req, res) -> res.status(BAD_REQUEST).send();
        }
    }

    private void configureReactorNettyMetrics() {
        Metrics.globalRegistry
            .config()
            .meterFilter(MeterFilter.maximumAllowableTags(HTTP_CLIENT_PREFIX, URI, REACTOR_NETTY_METRICS_MAX_URI_TAGS, MeterFilter.deny()));
    }

    private boolean wireTapEnabled() {
        return LoggerFactory.getLogger("com.linagora.calendar.restapi.wire").isTraceEnabled();
    }

    @PreDestroy
    public void stop() {
        server.ifPresent(DisposableServer::disposeNow);
    }
}
