package com.linagora.tmail.webadmin.quota.recompute;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TMailQuotaRootResolver;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class RecomputeQuotaTeamMailboxesService {
    public static class Context {
        static class Snapshot {
            private final long processedQuotaRootCount;
            private final ImmutableList<QuotaRoot> failedQuotaRoots;

            private Snapshot(long processedQuotaRootCount, ImmutableList<QuotaRoot> failedQuotaRoots) {
                this.processedQuotaRootCount = processedQuotaRootCount;
                this.failedQuotaRoots = failedQuotaRoots;
            }

            long getProcessedQuotaRootCount() {
                return processedQuotaRootCount;
            }

            ImmutableList<QuotaRoot> getFailedQuotaRoots() {
                return failedQuotaRoots;
            }

            @Override
            public final boolean equals(Object o) {
                if (o instanceof Snapshot) {
                    Snapshot that = (Snapshot) o;

                    return Objects.equals(this.processedQuotaRootCount, that.processedQuotaRootCount)
                        && Objects.equals(this.failedQuotaRoots, that.failedQuotaRoots);
                }
                return false;
            }

            @Override
            public final int hashCode() {
                return Objects.hash(processedQuotaRootCount, failedQuotaRoots);
            }

            @Override
            public String toString() {
                return MoreObjects.toStringHelper(this)
                    .add("processedQuotaRootCount", processedQuotaRootCount)
                    .add("failedQuotaRoots", failedQuotaRoots)
                    .toString();
            }
        }

        private final AtomicLong processedQuotaRootCount;
        private final ConcurrentLinkedDeque<QuotaRoot> failedQuotaRoots;

        public Context() {
            this.processedQuotaRootCount = new AtomicLong();
            this.failedQuotaRoots = new ConcurrentLinkedDeque<>();
        }

        public Context(long processedQuotaRootCount, Collection<QuotaRoot> failedQuotaRoots) {
            this.processedQuotaRootCount = new AtomicLong(processedQuotaRootCount);
            this.failedQuotaRoots = new ConcurrentLinkedDeque<>(failedQuotaRoots);
        }

        void incrementProcessed() {
            processedQuotaRootCount.incrementAndGet();
        }

        void addToFailedMailboxes(QuotaRoot quotaRoot) {
            failedQuotaRoots.add(quotaRoot);
        }

        public Snapshot snapshot() {
            return new Snapshot(processedQuotaRootCount.get(),
                ImmutableList.copyOf(failedQuotaRoots));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RecomputeQuotaTeamMailboxesService.class);
    private final SessionProvider sessionProvider;
    private final CurrentQuotaCalculator currentQuotaCalculator;
    private final CurrentQuotaManager storeCurrentQuotaManager;
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public RecomputeQuotaTeamMailboxesService(SessionProvider sessionProvider,
                                              TMailQuotaRootResolver quotaRootResolver,
                                              MailboxSessionMapperFactory mailboxSessionMapperFactory,
                                              CurrentQuotaManager storeCurrentQuotaManager,
                                              TeamMailboxRepository teamMailboxRepository) {
        this.sessionProvider = sessionProvider;
        this.teamMailboxRepository = teamMailboxRepository;
        this.currentQuotaCalculator = new CurrentQuotaCalculator(mailboxSessionMapperFactory, quotaRootResolver);
        this.storeCurrentQuotaManager = storeCurrentQuotaManager;
    }

    public Mono<Task.Result> recompute(Domain domain, Context context) {
        return Flux.from(teamMailboxRepository.listTeamMailboxes(domain))
            .flatMap(teamMailbox -> recompute(teamMailbox, context))
            .reduce(Task.Result.COMPLETED, Task::combine);
    }

    public Mono<Task.Result> recompute(TeamMailbox teamMailbox, Context context) {
        QuotaRoot quotaRoot = teamMailbox.quotaRoot();
        return currentQuotaCalculator.recalculateCurrentQuotas(quotaRoot, sessionProvider.createSystemSession(teamMailbox.owner()))
            .map(recalculatedQuotas -> QuotaOperation.from(quotaRoot, recalculatedQuotas))
            .flatMap(quotaOperation -> Mono.from(storeCurrentQuotaManager.setCurrentQuotas(quotaOperation)))
            .then(Mono.just(Task.Result.COMPLETED))
            .doOnNext(any -> {
                LOGGER.info("Current quotas recomputed for {}", quotaRoot);
                context.incrementProcessed();
            })
            .onErrorResume(e -> {
                LOGGER.error("Error while recomputing current quotas for {}", quotaRoot, e);
                context.addToFailedMailboxes(quotaRoot);
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
