package com.linagora.tmail.webadmin.quota.recompute;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Preconditions;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import spark.Request;
import spark.Service;

public class RecomputeQuotaTeamMailboxesRoutes implements Routes {
    private static final String TEAM_MAILBOX_DOMAIN_PARAM = ":dom";
    private static final String RECOMPUTE_QUOTAS_TASK = "RecomputeQuotas";
    public static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + TEAM_MAILBOX_DOMAIN_PARAM + Constants.SEPARATOR + "team-mailboxes";

    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;
    private final RecomputeQuotaTeamMailboxesService recomputeQuotasService;
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public RecomputeQuotaTeamMailboxesRoutes(TaskManager taskManager,
                                             JsonTransformer jsonTransformer,
                                             RecomputeQuotaTeamMailboxesService recomputeQuotasService,
                                             TeamMailboxRepository teamMailboxRepository) {
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
        this.recomputeQuotasService = recomputeQuotasService;
        this.teamMailboxRepository = teamMailboxRepository;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest recomputeQuotaTaskRequest = this::recomputeQuota;
        service.post(BASE_PATH, recomputeQuotaTaskRequest.asRoute(taskManager), jsonTransformer);
    }

    private Domain extractDomain(Request request) {
        return Domain.of(request.params(TEAM_MAILBOX_DOMAIN_PARAM));
    }

    public Task recomputeQuota(Request request) {
        Preconditions.checkArgument(Optional.ofNullable(request.queryParams("task"))
                .filter(RECOMPUTE_QUOTAS_TASK::equals)
                .isPresent(),
            String.format("'task' is missing or must be '%s'", RECOMPUTE_QUOTAS_TASK));

        Domain domain = extractDomain(request);
        domainPreconditions(domain);
        return new RecomputeQuotaTeamMailboxesTask(recomputeQuotasService, domain);
    }

    private void domainPreconditions(Domain domain) {
        if (Flux.from(teamMailboxRepository.listTeamMailboxes(domain)).collectList().block().isEmpty()) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorResponder.ErrorType.NOT_FOUND)
                .message("The requested domain does not have any team mailbox")
                .haltError();
        }
    }
}
