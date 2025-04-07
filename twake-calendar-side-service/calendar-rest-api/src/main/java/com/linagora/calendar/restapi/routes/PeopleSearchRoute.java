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

import jakarta.inject.Inject;

import org.apache.james.jmap.Endpoint;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.metrics.api.MetricFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.linagora.calendar.restapi.RestApiConfiguration;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class PeopleSearchRoute extends CalendarRoute {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchRequestDTO {
        private final String q;
        private final List<String> objectTypes; // ignored
        private final int limit;

        @JsonCreator
        public SearchRequestDTO(@JsonProperty("q") String q,
                                @JsonProperty("objectTypes") List<String> objectTypes,
                                @JsonProperty("limit") int limit) {
            this.q = q;
            this.objectTypes = objectTypes;
            this.limit = limit;
        }
    }

    public static class ResponseDTO {
        private final String id;
        private final String emailAddress;
        private final String displayName;
        private final String photoUrl;

        public ResponseDTO(String id, String emailAddress, String displayName, String photoUrl) {
            this.id = id;
            this.emailAddress = emailAddress;
            this.displayName = displayName;
            this.photoUrl = photoUrl;
        }

        public String getId() {
            return id;
        }

        public String getObjectType() {
            return "contact";
        }

        public List<JsonNode> getEmailAddresses() {
            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            objectNode.put("value", emailAddress);
            objectNode.put("type", "Work");
            return ImmutableList.of(objectNode);
        }

        public List<String> getPhoneNumbers() {
            return ImmutableList.of();
        }

        public List<JsonNode> getNames() {
            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            objectNode.put("displayName", displayName);
            objectNode.put("type", "default");
            return ImmutableList.of(objectNode);
        }

        public List<JsonNode> getPhotos() {
            ObjectNode objectNode = OBJECT_MAPPER.createObjectNode();
            objectNode.put("url", photoUrl);
            objectNode.put("type", "default");
            return ImmutableList.of(objectNode);
        }
    }

    private final EmailAddressContactSearchEngine searchEngine;
    private final RestApiConfiguration configuration;

    @Inject
    public PeopleSearchRoute(Authenticator authenticator, MetricFactory metricFactory,
                             EmailAddressContactSearchEngine searchEngine,
                             RestApiConfiguration configuration) {
        super(authenticator, metricFactory);
        this.searchEngine = searchEngine;
        this.configuration = configuration;
    }

    @Override
    Endpoint endpoint() {
        return new Endpoint(HttpMethod.POST, "/api/people/search");
    }

    @Override
    Mono<Void> handleRequest(HttpServerRequest req, HttpServerResponse res, MailboxSession session) {
        return req.receive().aggregate().asByteArray()
            .map(Throwing.function(bytes -> OBJECT_MAPPER.readValue(bytes, SearchRequestDTO.class)))
            .flatMapMany(requestDTO -> {
                Preconditions.checkArgument(requestDTO.limit < 256, "Maximum limit allowed: 256. Got " + requestDTO.limit);

                return Flux.from(searchEngine.autoComplete(AccountId.fromString(session.getUser().asString()), requestDTO.q, requestDTO.limit));
            })
            .map(contact -> new ResponseDTO(contact.id().toString(),
                contact.fields().address().asString(),
                contact.fields().fullName(),
                configuration.getSelfUrl().toString() + "/api/avatars?email=" + contact.fields().address().asString()))
            .collectList()
            .map(Throwing.function(OBJECT_MAPPER::writeValueAsBytes))
            .flatMap(bytes -> res.status(200)
                .header("Content-Type", "application/json;charset=utf-8")
                .sendByteArray(Mono.just(bytes))
                .then());
    }
}
