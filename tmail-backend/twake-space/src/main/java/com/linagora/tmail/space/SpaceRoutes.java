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

package com.linagora.tmail.space;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.team.TeamMailbox;

import scala.jdk.javaapi.OptionConverters;
import spark.HaltException;
import spark.Request;
import spark.Route;
import spark.Service;

/**
 * Repairs of the space extension: asking ldap-rest for a snapshot, and binding a team mailbox to a space.
 */
public class SpaceRoutes implements Routes {
    public record SpaceBindingDTO(String organizationId, String spaceId) {
    }

    public record SpaceBindingResponse(String organizationId, String spaceId, Optional<String> deletedAt) {
        static SpaceBindingResponse from(SpaceBinding binding) {
            return new SpaceBindingResponse(binding.organizationId(), binding.spaceId(), binding.deletedAt().map(Object::toString));
        }
    }

    private static final String DOMAIN_PARAM = ":dom";
    private static final String NAME_PARAM = ":name";
    public static final String SPACES_PATH = "/spaces";
    public static final String BINDING_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + DOMAIN_PARAM
        + Constants.SEPARATOR + "team-mailboxes" + Constants.SEPARATOR + NAME_PARAM + Constants.SEPARATOR + "space";
    private static final JsonExtractor<SpaceBindingDTO> BINDING_EXTRACTOR = new JsonExtractor<>(SpaceBindingDTO.class);

    private final SpaceReconciler reconciler;
    private final SpacePublisher publisher;
    private final JsonTransformer jsonTransformer;

    @Inject
    public SpaceRoutes(SpaceReconciler reconciler, SpacePublisher publisher, JsonTransformer jsonTransformer) {
        this.reconciler = reconciler;
        this.publisher = publisher;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return SPACES_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(SPACES_PATH, requestSync(), jsonTransformer);
        service.get(BINDING_PATH, getBinding(), jsonTransformer);
        service.put(BINDING_PATH, bind(), jsonTransformer);
    }

    private Route requestSync() {
        return (request, response) -> {
            String action = request.queryParams("action");
            if (!"sync".equals(action)) {
                throw badRequest("Invalid action supplied: " + action + ". Supported actions: [sync]");
            }
            Optional<String> organizationId = queryParam(request, "organizationId");
            Optional<String> spaceId = queryParam(request, "spaceId");
            if (spaceId.isPresent() && organizationId.isEmpty()) {
                throw badRequest("'spaceId' needs 'organizationId'");
            }
            publisher.requestSync(organizationId, spaceId).block();
            return Responses.returnNoContent(response);
        };
    }

    private Route getBinding() {
        return (request, response) -> {
            SpaceMailbox spaceMailbox = readTeamMailbox(request);
            return spaceMailbox.binding()
                .map(SpaceBindingResponse::from)
                .orElseThrow(() -> notFound("The team mailbox is bound to no space"));
        };
    }

    private Route bind() {
        return (request, response) -> {
            SpaceBindingDTO binding = extractBinding(request);
            SpaceMailbox spaceMailbox = readTeamMailbox(request);
            spaceMailbox.binding()
                .filter(existing -> !existing.spaceId().equals(binding.spaceId()))
                .ifPresent(existing -> {
                    throw ErrorResponder.builder()
                        .statusCode(HttpStatus.CONFLICT_409)
                        .type(ErrorResponder.ErrorType.WRONG_STATE)
                        .message("The team mailbox is bound to space " + existing.spaceId())
                        .haltError();
                });
            reconciler.bind(spaceMailbox, binding.organizationId(), binding.spaceId()).block();
            return Responses.returnNoContent(response);
        };
    }

    private SpaceBindingDTO extractBinding(Request request) {
        try {
            SpaceBindingDTO binding = BINDING_EXTRACTOR.parse(request.body());
            if (isBlank(binding.organizationId()) || isBlank(binding.spaceId())) {
                throw badRequest("'organizationId' and 'spaceId' are compulsory");
            }
            return binding;
        } catch (JsonExtractException e) {
            throw badRequest("Invalid JSON body: " + e.getMessage());
        }
    }

    private SpaceMailbox readTeamMailbox(Request request) {
        TeamMailbox teamMailbox = teamMailbox(request);
        return reconciler.spaceMailboxes().read(teamMailbox)
            .blockOptional()
            .orElseThrow(() -> notFound("The requested team mailbox does not exists"));
    }

    private TeamMailbox teamMailbox(Request request) {
        try {
            Domain domain = Domain.of(request.params(DOMAIN_PARAM));
            return OptionConverters.toJava(TeamMailbox.fromJava(domain, request.params(NAME_PARAM)))
                .orElseThrow(() -> badRequest("Invalid team mailbox name: " + request.params(NAME_PARAM)));
        } catch (IllegalArgumentException e) {
            throw badRequest("Invalid domain: " + request.params(DOMAIN_PARAM));
        }
    }

    private Optional<String> queryParam(Request request, String name) {
        return Optional.ofNullable(request.queryParams(name)).filter(value -> !value.isBlank());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private HaltException badRequest(String message) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message(message)
            .haltError();
    }

    private HaltException notFound(String message) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message(message)
            .haltError();
    }
}
