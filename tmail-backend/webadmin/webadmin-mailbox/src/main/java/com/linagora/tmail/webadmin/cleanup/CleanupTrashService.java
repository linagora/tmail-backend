package com.linagora.tmail.webadmin.cleanup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CleanupTrashService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTrashService.class);
    private static final Map<String, Period> MAP_PERIOD_SETTING_TO_DURATION = ImmutableMap.of(JmapSettings.WeeklyPeriod(), Period.ofWeeks(1),
        JmapSettings.MonthlyPeriod(), Period.ofMonths(1));

    private final UsersRepository usersRepository;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final StoreMailboxManager storeMailboxManager;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final Clock clock;

    @Inject
    public CleanupTrashService(UsersRepository usersRepository, JmapSettingsRepository jmapSettingsRepository, StoreMailboxManager storeMailboxManager,
                               SystemMailboxesProvider systemMailboxesProvider, Clock clock) {
        this.usersRepository = usersRepository;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.storeMailboxManager = storeMailboxManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.clock = clock;
    }

    public Mono<Task.Result> cleanupTrash(RunningOptions runningOptions, CleanupContext context) {
        return Flux.from(usersRepository.listReactive())
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.getUsersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username -> cleanupTrashForSingleUser(username, context)))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Task.Result> cleanupTrashForSingleUser(Username username, CleanupContext context) {
        return Mono.from(jmapSettingsRepository.get(username))
            .flatMap(jmapSettings -> cleanupTrashForSingleUser(username, jmapSettings, context))
            .defaultIfEmpty(Task.Result.COMPLETED)
            .doOnNext(result -> {
                LOGGER.info("Trash mailbox is cleaned for user {}", username);
                context.incrementProcessed();
            }).onErrorResume(e -> {
                LOGGER.error("Error while cleaning trash for user {}", username, e);
                context.addToFailedUsers(username.asString());
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Task.Result> cleanupTrashForSingleUser(Username username, JmapSettings jmapSettings, CleanupContext context) {
        if (jmapSettings.trashCleanupEnabled()) {
            return cleanupTrashForSingleUser(username,
                toPeriod(jmapSettings.trashCleanupPeriod()),
                context);
        } else {
            return Mono.just(Task.Result.COMPLETED);
        }
    }

    private Period toPeriod(String trashCleanupPeriod) {
        return MAP_PERIOD_SETTING_TO_DURATION.getOrDefault(trashCleanupPeriod, Period.ofMonths(1));
    }

    private Mono<Task.Result> cleanupTrashForSingleUser(Username username, Period period, CleanupContext context) {
        return getMessageManagerForTrashMailbox(username)
            .flatMap(messageManager ->
                deleteExpiredMessages(getExpiredDate(period),
                    context,
                    messageManager,
                    getMailboxSession(username)))
            .then(Mono.just(Task.Result.COMPLETED));
    }

    private MailboxSession getMailboxSession(Username username) {
        return storeMailboxManager.getSessionProvider().createSystemSession(username);
    }

    private Mono<MessageManager> getMessageManagerForTrashMailbox(Username username) {
        return Mono.from(systemMailboxesProvider.getMailboxByRole(Role.TRASH, username));
    }

    private Mono<Void> deleteExpiredMessages(Instant expiredDate, CleanupContext context, MessageManager messageManager, MailboxSession mailboxSession) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession))
            .filter(messageResult -> messageResult.getSaveDate().orElse(messageResult.getInternalDate()).toInstant().isBefore(expiredDate))
            .map(MessageResult::getUid)
            .collect(ImmutableList.toImmutableList())
            .flatMap(messageUids -> messageManager.deleteReactive(messageUids, mailboxSession)
                .doOnSuccess(unused -> {
                    context.incrementDeletedMessagesCount(messageUids.size());
                })
            );
    }

    private Instant getExpiredDate(Period period) {
        return Instant.now(clock).atZone(ZoneOffset.UTC)
            .minus(period)
            .toInstant();
    }
}
