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

package com.linagora.tmail.webadmin.cleanup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CleanupService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupService.class);
    private final UsersRepository usersRepository;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final SessionProvider sessionProvider;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final Clock clock;

    @Inject
    public CleanupService(UsersRepository usersRepository, JmapSettingsRepository jmapSettingsRepository, SessionProvider sessionProvider,
                               SystemMailboxesProvider systemMailboxesProvider, Clock clock) {
        this.usersRepository = usersRepository;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.sessionProvider = sessionProvider;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.clock = clock;
    }

    public Mono<Task.Result> cleanup(Role role, RunningOptions runningOptions, CleanupContext context) {
        return Flux.from(usersRepository.listReactive())
            .transform(ReactorUtils.<Username, Task.Result>throttle()
                .elements(runningOptions.getUsersPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(username -> cleanupForSingleUser(role, username, context)))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(e -> {
                LOGGER.error("Error while accessing users from repository", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Task.Result> cleanupForSingleUser(Role role, Username username, CleanupContext context) {
        return Mono.from(jmapSettingsRepository.get(username))
            .filter(jmapSettings -> checkCleanupEnabled(role, jmapSettings))
            .flatMap(jmapSettings -> cleanupForSingleUser(role, username, periodByRole(role, jmapSettings), context))
            .defaultIfEmpty(Task.Result.COMPLETED)
            .onErrorResume(e -> {
                LOGGER.error("Error while cleaning mailbox {} for user {}", role.serialize(), username, e);
                context.addToFailedUsers(username.asString());
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private boolean checkCleanupEnabled(Role role, JmapSettings jmapSettings) {
        return Role.TRASH.equals(role) && jmapSettings.trashCleanupEnabled()
            || Role.SPAM.equals(role) && jmapSettings.spamCleanupEnabled();
    }

    private Period periodByRole(Role role, JmapSettings jmapSettings) {
        if (Role.TRASH.equals(role)) {
            return jmapSettings.trashCleanupPeriod();
        }
        return jmapSettings.spamCleanupPeriod();
    }

    private Mono<Task.Result> cleanupForSingleUser(Role role, Username username, Period period, CleanupContext context) {
        return getMessageManagerForMailbox(role, username)
            .flatMap(messageManager ->
                deleteExpiredMessages(getExpiredDate(period),
                    context,
                    messageManager,
                    getMailboxSession(username)))
            .then(Mono.just(Task.Result.COMPLETED))
            .doOnNext(result -> {
                LOGGER.info("Mailbox {} is cleaned for user {}", role.serialize(), username);
                context.incrementProcessed();
            });
    }

    private MailboxSession getMailboxSession(Username username) {
        return sessionProvider.createSystemSession(username);
    }

    private Mono<MessageManager> getMessageManagerForMailbox(Role role, Username username) {
        return Mono.from(systemMailboxesProvider.getMailboxByRole(role, username));
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
