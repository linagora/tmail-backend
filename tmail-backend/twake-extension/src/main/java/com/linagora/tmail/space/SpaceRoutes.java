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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
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
import spark.Response;
import spark.Route;
import spark.Service;

/**
 * Repairs of the space extension: asking ldap-rest for a snapshot, and binding a team mailbox to a space.
 */
public class SpaceRoutes implements Routes {
    /** {@code deletedAt}, optional, binds a deleted space, like one migrated from the Twake Mail side service. */
    public record SpaceBindingDTO(String organizationId, String spaceId, String deletedAt) {
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
    private final SpacePurge purge;
    private final SpacePublisher publisher;
    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;

    @Inject
    public SpaceRoutes(SpaceReconciler reconciler, SpacePurge purge, SpacePublisher publisher, TaskManager taskManager,
                       JsonTransformer jsonTransformer) {
        this.reconciler = reconciler;
        this.purge = purge;
        this.publisher = publisher;
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return SPACES_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(SPACES_PATH, spacesAction(), jsonTransformer);
        service.get(BINDING_PATH, getBinding(), jsonTransformer);
        service.put(BINDING_PATH, bind(), jsonTransformer);
    }

    private Route spacesAction() {
        TaskFromRequest purgeTask = request -> new SpacePurgeTask(this.purge);
        TaskFromRequest reindexTask = request -> new SpaceReindexTask(reconciler);
        Route purgeRoute = purgeTask.asRoute(taskManager);
        Route reindexRoute = reindexTask.asRoute(taskManager);
        return (request, response) -> {
            String action = request.queryParams("action");
            if ("sync".equals(action)) {
                return requestSync(request, response);
            }
            if ("purge".equals(action)) {
                return purgeRoute.handle(request, response);
            }
            if ("reindex".equals(action)) {
                return reindexRoute.handle(request, response);
            }
            throw badRequest("Invalid action supplied: " + action + ". Supported actions: [sync, purge, reindex]");
        };
    }

    private Object requestSync(Request request, Response response) {
        Optional<String> organizationId = queryParam(request, "organizationId");
        Optional<String> spaceId = queryParam(request, "spaceId");
        if (spaceId.isPresent() && organizationId.isEmpty()) {
            throw badRequest("'spaceId' needs 'organizationId'");
        }
        publisher.requestSync(new SpacePublisher.SyncScope(organizationId, spaceId)).block();
        return Responses.returnNoContent(response);
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
            Optional<Instant> deletedAt = deletedAt(binding);
            SpaceMailbox spaceMailbox = readTeamMailbox(request);
            spaceMailbox.binding()
                .filter(existing -> !existing.spaceId().equals(binding.spaceId()))
                .ifPresent(existing -> {
                    throw conflict("The team mailbox is bound to space " + existing.spaceId());
                });
            reconciler.indexedMailbox(spaceMailbox.teamMailbox().domain(), binding.spaceId())
                .filter(indexed -> !indexed.teamMailbox().equals(spaceMailbox.teamMailbox()))
                .blockOptional()
                .ifPresent(indexed -> {
                    throw conflict("Space " + binding.spaceId() + " already has the team mailbox "
                        + indexed.teamMailbox().asMailAddress().asString());
                });
            SpaceRef space = new SpaceRef(binding.organizationId(), binding.spaceId());
            deletedAt
                .map(instant -> reconciler.bindClosed(spaceMailbox, space, instant))
                .orElseGet(() -> reconciler.bind(spaceMailbox, space))
                .block();
            return Responses.returnNoContent(response);
        };
    }

    private Optional<Instant> deletedAt(SpaceBindingDTO binding) {
        try {
            return Optional.ofNullable(binding.deletedAt()).map(Instant::parse);
        } catch (DateTimeParseException e) {
            throw badRequest("'deletedAt' must be an ISO 8601 instant: " + binding.deletedAt());
        }
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

    private HaltException conflict(String message) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.CONFLICT_409)
            .type(ErrorResponder.ErrorType.WRONG_STATE)
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
