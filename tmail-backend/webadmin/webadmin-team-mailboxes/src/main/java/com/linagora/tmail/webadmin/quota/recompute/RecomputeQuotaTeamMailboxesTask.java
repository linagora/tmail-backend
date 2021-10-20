package com.linagora.tmail.webadmin.quota.recompute;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesService.Context;

import reactor.core.scheduler.Schedulers;

public class RecomputeQuotaTeamMailboxesTask implements Task {
    static final TaskType TASK_TYPE = TaskType.of("recompute-quota-team-mailboxes");

    public static class Details implements TaskExecutionDetails.AdditionalInformation {
        private final Instant instant;
        private final Domain domain;
        private final long processedQuotaRoots;
        private final ImmutableList<String> failedQuotaRoots;

        public Details(Instant instant, Domain domain,
                       long processedQuotaRoots, ImmutableList<String> failedQuotaRoots) {
            this.instant = instant;
            this.domain = domain;
            this.processedQuotaRoots = processedQuotaRoots;
            this.failedQuotaRoots = failedQuotaRoots;
        }

        public Domain getDomain() {
            return domain;
        }

        public long getProcessedQuotaRoots() {
            return processedQuotaRoots;
        }

        public ImmutableList<String> getFailedQuotaRoots() {
            return failedQuotaRoots;
        }

        @Override
        public Instant timestamp() {
            return instant;
        }
    }

    private final RecomputeQuotaTeamMailboxesService service;
    private final Domain teamMailboxDomain;
    private final Context context;

    public RecomputeQuotaTeamMailboxesTask(RecomputeQuotaTeamMailboxesService service,
                                           Domain teamMailboxDomain) {
        this.service = service;
        this.teamMailboxDomain = teamMailboxDomain;
        this.context = new Context();
    }

    @Override
    public Result run() {
        return service.recompute(teamMailboxDomain, context)
            .subscribeOn(Schedulers.elastic())
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        Context.Snapshot snapshot = context.snapshot();
        return Optional.of(new Details(
            Clock.systemUTC().instant(),
            teamMailboxDomain,
            snapshot.getProcessedQuotaRootCount(),
            snapshot.getFailedQuotaRoots()
                .stream()
                .map(QuotaRoot::asString)
                .collect(ImmutableList.toImmutableList())));
    }
}
