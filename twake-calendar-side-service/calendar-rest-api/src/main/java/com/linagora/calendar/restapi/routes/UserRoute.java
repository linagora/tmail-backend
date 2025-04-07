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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.calendar.restapi.NotFoundException;
import com.linagora.calendar.storage.OpenPaaSDomainDAO;
import com.linagora.calendar.storage.OpenPaaSId;
import com.linagora.calendar.storage.OpenPaaSUser;
import com.linagora.calendar.storage.OpenPaaSUserDAO;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class UserRoute extends CalendarRoute {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static class ResponseDTO {
        private final OpenPaaSUser user;
        private final OpenPaaSId domainId;

        public ResponseDTO(OpenPaaSUser user, OpenPaaSId domainId) {
            this.user = user;
            this.domainId = domainId;
        }

        @JsonProperty("_id")
        public String getId() {
            return user.id().value();
        }

        @JsonProperty("preferredEmail")
        public String getName() {
            return user.username().asString();
        }

        @JsonProperty("objectType")
        public String getOpbjectType() {
            return "user";
        }

        @JsonProperty("main_phone")
        public String getMainPhone() {
            return "";
        }

        @JsonProperty("followings")
        public int getFollowings() {
            return 0;
        }

        @JsonProperty("following")
        public boolean getFollowing() {
            return false;
        }

        @JsonProperty("followers")
        public int getFollowers() {
            return 0;
        }

        @JsonProperty("domains")
        public ImmutableList<DomainInfo> getDomains() {
            return ImmutableList.of(new DomainInfo(domainId));
        }

        @JsonProperty("state")
        public ImmutableList<String> getState() {
            return ImmutableList.of();
        }

        @JsonProperty("emails")
        public ImmutableList<String> getEmails() {
            return ImmutableList.of(user.username().asString());
        }

        @JsonProperty("firstname")
        public String getFirstname() {
            return user.firstname();
        }

        @JsonProperty("lastname")
        public String getLastname() {
            return user.lastname();
        }
    }

    public static class DomainInfo {
        private final OpenPaaSId domainId;

        public DomainInfo(OpenPaaSId domainId) {
            this.domainId = domainId;
        }

        @JsonProperty("domain_id")
        public String getDomainId() {
            return domainId.value();
        }

        @JsonProperty("joined_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        public ZonedDateTime getJoinedAt() {
            return ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private final OpenPaaSUserDAO userDAO;
    private final OpenPaaSDomainDAO domainDAO;

    @Inject
    public UserRoute(Authenticator authenticator, MetricFactory metricFactory, OpenPaaSUserDAO userDAO, OpenPaaSDomainDAO domainDAO) {
        super(authenticator, metricFactory);
        this.userDAO = userDAO;
        this.domainDAO = domainDAO;
    }

    @Override
    Endpoint endpoint() {
        return new Endpoint(HttpMethod.GET, "/api/users/{userId}");
    }

    @Override
    Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response, MailboxSession session) {
        OpenPaaSId userId = new OpenPaaSId(request.param("userId"));
        return userDAO.retrieve(userId)
            .flatMap(user -> domainDAO.retrieve(user.username().getDomainPart().get())
                .map(domain -> new ResponseDTO(user, domain.id())))
            .map(Throwing.function(OBJECT_MAPPER::writeValueAsBytes))
            .switchIfEmpty(Mono.error(NotFoundException::new))
            .flatMap(bytes -> response.status(200)
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Cache-Control", "max-age=60, public")
                .sendByteArray(Mono.just(bytes))
                .then());
    }
}
