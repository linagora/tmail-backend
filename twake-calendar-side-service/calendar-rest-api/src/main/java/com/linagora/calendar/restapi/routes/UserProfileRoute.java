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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import jakarta.inject.Inject;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.calendar.restapi.routes.configuration.ConfigurationResolver;
import com.linagora.calendar.storage.OpenPaaSDomainDAO;
import com.linagora.calendar.storage.OpenPaaSId;
import com.linagora.calendar.storage.OpenPaaSUser;
import com.linagora.calendar.storage.OpenPaaSUserDAO;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class UserProfileRoute extends CalendarRoute {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static class ProfileResponseDTO extends UserRoute.ResponseDTO {
        private final JsonNode configuration;
        private final OpenPaaSUser user;

        public ProfileResponseDTO(OpenPaaSUser user, OpenPaaSId domainId, JsonNode configuration) {
            super(user, domainId);
            this.configuration = configuration;
            this.user = user;
        }

        @JsonProperty("isPlatformAdmin")
        public boolean isPlatformAdmin() {
            return false;
        }

        @JsonProperty("id")
        public String id() {
            return getId();
        }

        @JsonProperty("login")
        public LoginDTO getLogin() {
            return new LoginDTO();
        }

        @JsonProperty("accounts")
        public ImmutableList<AccountDTO> getAccount() {
            return ImmutableList.of(new AccountDTO(user));
        }

        @JsonProperty("configurations")
        public JsonNode getConfigurations() {
            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            objectNode.put("modules", configuration);
            return objectNode;
        }
    }

    public static class LoginDTO {
        @JsonProperty("success")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        public ZonedDateTime getJoinedAt() {
            return ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }

        @JsonProperty("failures")
        public ImmutableList<String> getFailures() {
            return ImmutableList.of();
        }
    }

    public static class AccountDTO {
        private final OpenPaaSUser user;

        public AccountDTO(OpenPaaSUser user) {
            this.user = user;
        }

        @JsonProperty("hosted")
        public boolean getHosted() {
            return true;
        }

        @JsonProperty("preferredEmailIndex")
        public int getPreferedIndex() {
            return 0;
        }

        @JsonProperty("type")
        public String getType() {
            return "email";
        }

        @JsonProperty("emails")
        public ImmutableList<String> getEmails() {
            return ImmutableList.of(user.username().asString());
        }

        @JsonProperty("timestamps")
        public DomainRoute.Timestamp getTimestamps() {
            return new DomainRoute.Timestamp();
        }
    }

    private final OpenPaaSUserDAO userDAO;
    private final OpenPaaSDomainDAO domainDAO;
    private final ConfigurationResolver configurationResolver;

    @Inject
    public UserProfileRoute(Authenticator authenticator, MetricFactory metricFactory, OpenPaaSUserDAO userDAO, OpenPaaSDomainDAO domainDAO, ConfigurationResolver configurationResolver) {
        super(authenticator, metricFactory);
        this.userDAO = userDAO;
        this.domainDAO = domainDAO;
        this.configurationResolver = configurationResolver;
    }

    @Override
    Endpoint endpoint() {
        return new Endpoint(HttpMethod.GET, "/api/user");
    }

    @Override
    Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response, MailboxSession session) {
        return configurationResolver.resolveAll(session)
            .flatMap(conf ->  userDAO.retrieve(session.getUser())
                .flatMap(user -> domainDAO.retrieve(session.getUser().getDomainPart().get())
                    .map(domain -> new ProfileResponseDTO(user, domain.id(), conf.asJson()))))
            .map(Throwing.function(OBJECT_MAPPER::writeValueAsBytes))
            .flatMap(bytes -> response.status(200)
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Cache-Control", "max-age=60, public")
                .sendByteArray(Mono.just(bytes))
                .then());
    }
}
