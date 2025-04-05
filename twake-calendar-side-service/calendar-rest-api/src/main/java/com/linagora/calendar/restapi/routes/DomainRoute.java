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
import com.linagora.calendar.storage.OpenPaaSDomain;
import com.linagora.calendar.storage.OpenPaaSDomainDAO;
import com.linagora.calendar.storage.OpenPaaSId;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class DomainRoute extends CalendarRoute {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static class ResponseDTO {
        private final OpenPaaSDomain domain;

        public ResponseDTO(OpenPaaSDomain domain) {
            this.domain = domain;
        }

        @JsonProperty("timestamps")
        public Timestamp getTimestamps() {
            return new Timestamp();
        }

        @JsonProperty("hostnames")
        public ImmutableList<String> getHostnames() {
            return ImmutableList.of(domain.domain().asString());
        }

        @JsonProperty("schemaVersion")
        public int getSchemaVersion() {
            return 1;
        }

        @JsonProperty("_id")
        public String getId() {
            return domain.id().value();
        }

        @JsonProperty("name")
        public String getName() {
            return domain.domain().asString();
        }

        @JsonProperty("company_name")
        public String getCompanyName() {
            return domain.domain().asString();
        }

        @JsonProperty("__v")
        public int getV() {
            return 0;
        }

        @JsonProperty("administrators")
        public ImmutableList<String> getAdministrators() {
            return ImmutableList.of();
        }

        @JsonProperty("injections")
        public ImmutableList<String> getIjections() {
            return ImmutableList.of();
        }
    }

    public static class Timestamp {
        @JsonProperty("creation")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        public ZonedDateTime getCreation() {
            return ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }

    private final OpenPaaSDomainDAO domainDAO;

    @Inject
    public DomainRoute(Authenticator authenticator, MetricFactory metricFactory, OpenPaaSDomainDAO domainDAO) {
        super(authenticator, metricFactory);
        this.domainDAO = domainDAO;
    }

    @Override
    Endpoint endpoint() {
        return new Endpoint(HttpMethod.GET, "/api/domains/{domainId}");
    }

    @Override
    Mono<Void> handleRequest(HttpServerRequest request, HttpServerResponse response, MailboxSession session) {
        OpenPaaSId domainId = new OpenPaaSId(request.param("domainId"));
        return domainDAO.retrieve(domainId)
            .map(ResponseDTO::new)
            .map(Throwing.function(OBJECT_MAPPER::writeValueAsBytes))
            .flatMap(bytes -> response.status(200)
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Cache-Control", "max-age=60, public")
                .sendByteArray(Mono.just(bytes))
                .then());
    }
}
