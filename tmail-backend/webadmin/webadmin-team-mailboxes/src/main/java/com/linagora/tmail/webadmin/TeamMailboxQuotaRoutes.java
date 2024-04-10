package com.linagora.tmail.webadmin;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.QuotaDTO;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.JsonTransformerModule;
import org.apache.james.webadmin.utils.Responses;
import org.apache.james.webadmin.validation.QuotaDTOValidator;
import org.apache.james.webadmin.validation.Quotas;
import org.eclipse.jetty.http.HttpStatus;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Mono;
import spark.Request;
import spark.Route;
import spark.Service;

public class TeamMailboxQuotaRoutes implements Routes {
    private static final String TEAM_MAILBOX_DOMAIN_PARAM = ":dom";
    private static final String TEAM_MAILBOX_NAME_PARAM = ":name";
    public static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + TEAM_MAILBOX_DOMAIN_PARAM + Constants.SEPARATOR + "team-mailboxes" + Constants.SEPARATOR + TEAM_MAILBOX_NAME_PARAM + Constants.SEPARATOR + "quota";
    public static final String LIMIT_PATH = BASE_PATH + Constants.SEPARATOR + "limit";
    public static final String COUNT_LIMIT_PATH = LIMIT_PATH + Constants.SEPARATOR + "count";
    public static final String SIZE_LIMIT_PATH = LIMIT_PATH + Constants.SEPARATOR + "size";

    private final TeamMailboxRepository teamMailboxRepository;
    private final TeamMailboxQuotaService teamMailboxQuotaService;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<QuotaDTO> jsonExtractor;
    private final QuotaDTOValidator quotaDTOValidator;

    @Inject
    public TeamMailboxQuotaRoutes(TeamMailboxRepository teamMailboxRepository,
                                  TeamMailboxQuotaService teamMailboxQuotaService,
                                  JsonTransformer jsonTransformer,
                                  Set<JsonTransformerModule> modules) {
        this.teamMailboxRepository = teamMailboxRepository;
        this.teamMailboxQuotaService = teamMailboxQuotaService;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(QuotaDTO.class, modules.stream().map(JsonTransformerModule::asJacksonModule).toList());
        this.quotaDTOValidator = new QuotaDTOValidator();
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        service.get(COUNT_LIMIT_PATH, getQuotaCount(), jsonTransformer);
        service.put(COUNT_LIMIT_PATH, updateQuotaCount());
        service.delete(COUNT_LIMIT_PATH, deleteQuotaCount());

        service.get(SIZE_LIMIT_PATH, getQuotaSize(), jsonTransformer);
        service.put(SIZE_LIMIT_PATH, updateQuotaSize());
        service.delete(SIZE_LIMIT_PATH, deleteQuotaSize());

        service.put(LIMIT_PATH, updateQuota());
        service.get(BASE_PATH, getQuota(), jsonTransformer);
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(TEAM_MAILBOX_DOMAIN_PARAM));
    }

    private TeamMailboxName extractName(Request request) {
        return TeamMailboxName.fromString(request.params(TEAM_MAILBOX_NAME_PARAM))
            .fold(e -> {
                throw e;
            }, x -> x);
    }

    public Route getQuotaCount() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            Optional<QuotaCountLimit> maxCountQuota = teamMailboxQuotaService.getMaxCountQuota(teamMailbox);
            if (maxCountQuota.isPresent()) {
                return maxCountQuota;
            }
            return Responses.returnNoContent(response);
        };
    }

    private TeamMailbox checkTeamMailboxExists(Request request) {
        Domain domain = extractDomain(request);
        TeamMailboxName teamMailboxName = extractName(request);
        TeamMailbox teamMailbox = TeamMailbox.apply(domain, teamMailboxName);

        if (!(Boolean) Mono.from(teamMailboxRepository.exists(teamMailbox)).block()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("Team mailbox not found")
                .haltError();
        }
        return teamMailbox;
    }

    public Route updateQuotaCount() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            QuotaCountLimit quotaCount = Quotas.quotaCount(request.body());

            teamMailboxQuotaService.defineMaxCountQuota(teamMailbox, quotaCount);
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteQuotaCount() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            teamMailboxQuotaService.deleteMaxCountQuota(teamMailbox);
            return Responses.returnNoContent(response);
        };
    }

    public Route getQuotaSize() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            Optional<QuotaSizeLimit> maxSizeQuota = teamMailboxQuotaService.getMaxSizeQuota(teamMailbox);
            if (maxSizeQuota.isPresent()) {
                return maxSizeQuota;
            }
            return Responses.returnNoContent(response);
        };
    }

    public Route updateQuotaSize() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            QuotaSizeLimit quotaSize = Quotas.quotaSize(request.body());

            teamMailboxQuotaService.defineMaxSizeQuota(teamMailbox, quotaSize);
            return Responses.returnNoContent(response);
        };
    }

    public Route deleteQuotaSize() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            teamMailboxQuotaService.deleteMaxSizeQuota(teamMailbox);
            return Responses.returnNoContent(response);
        };
    }

    public Route updateQuota() {
        return (request, response) -> {
            try {
                TeamMailbox teamMailbox = checkTeamMailboxExists(request);
                QuotaDTO quotaDTO = jsonExtractor.parse(request.body());
                ValidatedQuotaDTO validatedQuotaDTO = quotaDTOValidator.validatedQuotaDTO(quotaDTO);
                teamMailboxQuotaService.defineQuota(teamMailbox, validatedQuotaDTO);
                return Responses.returnNoContent(response);
            } catch (IllegalArgumentException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                    .message("Quota should be positive or unlimited (-1)")
                    .cause(e)
                    .haltError();
            }
        };
    }

    public Route getQuota() {
        return (request, response) -> {
            TeamMailbox teamMailbox = checkTeamMailboxExists(request);
            return teamMailboxQuotaService.getQuota(teamMailbox);
        };
    }
}
