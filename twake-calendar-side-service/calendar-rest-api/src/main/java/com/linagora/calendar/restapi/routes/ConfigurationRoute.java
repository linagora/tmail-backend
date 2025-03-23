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

package com.linagora.calendar.restapi.routes;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.linagora.calendar.restapi.api.ConfigurationDocument;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;
import com.linagora.calendar.restapi.routes.configuration.ConfigurationResolver;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class ConfigurationRoute extends CalendarRoute {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static class RequestDTO {
        private final String name;
        private final List<String> keys;

        @JsonCreator
        public RequestDTO(@JsonProperty("name") String name, @JsonProperty("keys") List<String> keys) {
            this.name = name;
            this.keys = keys;
        }

        List<Pair<ModuleName, ConfigurationKey>> asConfigurationKeys() {
            ModuleName moduleName = new ModuleName(name);
            return keys.stream()
                .map(key -> Pair.of(moduleName, new ConfigurationKey(key)))
                .collect(Collectors.toList());
        }
    }

    private final ConfigurationResolver configurationResolver;

    @Inject
    public ConfigurationRoute(Authenticator authenticator, MetricFactory metricFactory, ConfigurationResolver configurationResolver) {
        super(authenticator, metricFactory);
        this.configurationResolver = configurationResolver;
    }

    @Override
    Endpoint endpoint() {
        return Endpoint.ofFixedPath(HttpMethod.POST, "/api/configurations");
    }

    @Override
    Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response, MailboxSession session) {
        return request.receive().aggregate().asByteArray()
            .map(Throwing.function(OBJECT_MAPPER::readTree))
            .map(node -> node.get(0))
            .map(Throwing.function(node -> OBJECT_MAPPER.treeToValue(node, RequestDTO.class)))
            .map(RequestDTO::asConfigurationKeys)
            .flatMap(keys -> configurationResolver.resolve(keys, session))
            .map(ConfigurationDocument::asJson)
            .map(Throwing.function(OBJECT_MAPPER::writeValueAsString))
            .flatMap(string -> response.status(HttpResponseStatus.OK)
                .sendString(Mono.just(string))
                .then());
    }
}
