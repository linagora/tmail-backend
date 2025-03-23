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

package com.linagora.calendar.restapi.routes.configuration;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.linagora.calendar.restapi.RestApiConfiguration;
import com.linagora.calendar.restapi.api.ConfigurationKey;
import com.linagora.calendar.restapi.api.ModuleName;
import com.linagora.calendar.restapi.routes.JwtSigner;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class FallbackConfigurationEntryResolver {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final Logger LOGGER = LoggerFactory.getLogger(FallbackConfigurationEntryResolver.class);
    private final RestApiConfiguration configuration;
    private final JwtSigner signer;
    private final HttpClient client;

    @Inject
    public FallbackConfigurationEntryResolver(RestApiConfiguration configuration, JwtSigner signer) {
        this.configuration = configuration;
        this.signer = signer;

        this.client = createClient();
    }

    private HttpClient createClient() {
        if (configuration.openpaasBackendTrustAllCerts()) {
            return HttpClient.create().secure(sslContextSpec -> sslContextSpec.sslContext(
                SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)));
        }
        return HttpClient.create();
    }

    public Mono<JsonNode> resolve(ModuleName moduleName, ConfigurationKey configurationKey, MailboxSession session) {
        LOGGER.warn("Not implemented configuration provider name:{} key:{}",
            moduleName.name(), configurationKey.value());

        String payload = "[{\"name\":\"" + moduleName.name() + "\",\"keys\":[\"" + configurationKey.value() + "\"]}]";

        return signer.generate(session.getUser().asString())
            .flatMap(token -> client.headers(headers -> {
                    headers.add(HttpHeaderNames.CONTENT_TYPE, "application/json");
                    headers.add(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
                })
                .request(HttpMethod.POST)
                .uri(configuration.getOpenpaasBackendURL() + "/api/configurations?scope=user")
                .send(Mono.just(Unpooled.wrappedBuffer(payload.getBytes())))
                .response((response, body) -> {
                    if (response.status().equals(HttpResponseStatus.OK)) {
                        return body.aggregate().asByteArray()
                            .map(Throwing.function(OBJECT_MAPPER::readTree))
                            .map(this::extractValue);
                    }
                    throw new RuntimeException("Wrong response type " + response.status());
                }).single());
    }

    private JsonNode extractValue(JsonNode jsonNode) {
        return jsonNode.at("/[0].configurations[0].value");
    }
}
