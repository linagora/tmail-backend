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

package com.linagora.tmail.webadmin.jmap;

import static jakarta.mail.Flags.Flag.DELETED;
import static org.apache.james.mailbox.MailboxManager.MailboxSearchFetchType.Minimal;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedWildcard;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.projections.ConcernedKeywordsExtractor;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.MailboxReadRightsResolver;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class KeywordEmailQueryViewPopulator {
    static class Progress {
        private final AtomicLong processedUserCount;
        private final AtomicLong processedMessageCount;
        private final AtomicLong provisionedKeywordViewCount;
        private final AtomicLong failedUserCount;
        private final AtomicLong failedMessageCount;

        Progress() {
            this.processedUserCount = new AtomicLong();
            this.processedMessageCount = new AtomicLong();
            this.provisionedKeywordViewCount = new AtomicLong();
            this.failedUserCount = new AtomicLong();
            this.failedMessageCount = new AtomicLong();
        }

        void incrementProcessedUserCount() {
            processedUserCount.incrementAndGet();
        }

        void incrementProcessedMessageCount() {
            processedMessageCount.incrementAndGet();
        }

        void incrementProvisionedKeywordRowCount() {
            provisionedKeywordViewCount.incrementAndGet();
        }

        void incrementFailedUserCount() {
            failedUserCount.incrementAndGet();
        }

        void incrementFailedMessageCount() {
            failedMessageCount.incrementAndGet();
        }

        long getProcessedUserCount() {
            return processedUserCount.get();
        }

        long getProcessedMessageCount() {
            return processedMessageCount.get();
        }

        long getProvisionedKeywordViewCount() {
            return provisionedKeywordViewCount.get();
        }

        long getFailedUserCount() {
            return failedUserCount.get();
        }

        long getFailedMessageCount() {
            return failedMessageCount.get();
        }
    }

    private record ProvisionContext(Set<Username> readableUsers, MessageResult messageResult) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(KeywordEmailQueryViewPopulator.class);
    private final UsersRepository usersRepository;
    private final MailboxManager mailboxManager;
    private final KeywordEmailQueryView keywordEmailQueryView;
    private final MailboxReadRightsResolver mailboxReadRightsResolver;
    private final ConcernedKeywordsExtractor concernedKeywordsExtractor;
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public KeywordEmailQueryViewPopulator(UsersRepository usersRepository,
                                          MailboxManager mailboxManager,
                                          KeywordEmailQueryView keywordEmailQueryView,
                                          MailboxReadRightsResolver mailboxReadRightsResolver,
                                          ConcernedKeywordsExtractor concernedKeywordsExtractor,
                                          TeamMailboxRepository teamMailboxRepository) {
        this.usersRepository = usersRepository;
        this.mailboxManager = mailboxManager;
        this.keywordEmailQueryView = keywordEmailQueryView;
        this.mailboxReadRightsResolver = mailboxReadRightsResolver;
        this.concernedKeywordsExtractor = concernedKeywordsExtractor;
        this.teamMailboxRepository = teamMailboxRepository;
    }

    Mono<Task.Result> populateView(Progress progress, RunningOptions runningOptions) {
        return provisionKeywordView(listAllMailboxMessages(progress), runningOptions, progress);
    }

    private Mono<Task.Result> provisionKeywordView(Flux<ProvisionContext> entries, RunningOptions runningOptions, Progress progress) {
        return entries.transform(ReactorUtils.<ProvisionContext, Task.Result>throttle()
                .elements(runningOptions.messagesPerSecond())
                .per(Duration.ofSeconds(1))
                .forOperation(context -> provisionKeywordView(context, progress)))
            .reduce(Task::combine)
            .switchIfEmpty(Mono.just(Task.Result.COMPLETED))
            .map(taskResult -> hasFailures(progress) ? Task.Result.PARTIAL : taskResult);
    }

    private Flux<ProvisionContext> listAllMailboxMessages(Progress progress) {
        return Flux.merge(listPrivateMailboxMessages(progress), listTeamMailboxMessages())
            .filter(context -> !context.messageResult().getFlags().contains(DELETED));
    }

    private Flux<ProvisionContext> listPrivateMailboxMessages(Progress progress) {
        return Flux.from(usersRepository.listReactive())
            .map(mailboxManager::createSystemSession)
            .doOnNext(any -> progress.incrementProcessedUserCount())
            .concatMap(session -> listUserMailboxMessages(progress, session));
    }

    private Flux<ProvisionContext> listUserMailboxMessages(Progress progress, MailboxSession userSession) {
        return listUserPrivateMailboxes(userSession)
            .concatMap(this::listMailboxMessages)
            .onErrorResume(MailboxException.class, e -> {
                LOGGER.error("KeywordEmailQueryView population aborted for {} as we failed listing user mailboxes", userSession.getUser().asString(), e);
                progress.incrementFailedUserCount();
                return Flux.empty();
            });
    }

    private Mono<Task.Result> provisionKeywordView(ProvisionContext context, Progress progress) {
        return Mono.fromCallable(() -> concernedKeywordsExtractor.extract(context.messageResult().getFlags()))
            .flatMap(keywords -> {
                progress.incrementProcessedMessageCount();
                if (keywords.isEmpty()) {
                    return Mono.just(Task.Result.COMPLETED);
                }

                return Flux.fromIterable(context.readableUsers())
                    .concatMap(username -> saveKeywords(username, keywords, context.messageResult(), progress))
                    .then(Mono.just(Task.Result.COMPLETED));
            })
            .onErrorResume(e -> {
                LOGGER.error("KeywordEmailQueryView population aborted for {} - {} - {}",
                    context.messageResult().getMailboxId(),
                    context.messageResult().getMessageId(),
                    context.messageResult().getUid(), e);
                progress.incrementFailedMessageCount();
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Void> saveKeywords(Username username, Set<Keyword> keywords, MessageResult messageResult, Progress progress) {
        Instant receivedAt = messageResult.getInternalDate().toInstant();
        ThreadId threadId = messageResult.getThreadId();
        return Flux.fromIterable(keywords)
            .flatMap(keyword -> keywordEmailQueryView.save(username, keyword, receivedAt, messageResult.getMessageId(), threadId)
                .doOnSuccess(any -> progress.incrementProvisionedKeywordRowCount()), DEFAULT_CONCURRENCY)
            .then();
    }

    private boolean hasFailures(Progress progress) {
        return progress.getFailedUserCount() > 0 || progress.getFailedMessageCount() > 0;
    }

    private Flux<MailboxMetaData> listUserPrivateMailboxes(MailboxSession session) {
        return mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), Minimal, session);
    }

    private Flux<ProvisionContext> listTeamMailboxMessages() {
        return Flux.from(teamMailboxRepository.listTeamMailboxes())
            .concatMap(this::listTeamMailboxFolders)
            .concatMap(this::listMailboxMessages);
    }

    private Flux<MailboxMetaData> listTeamMailboxFolders(TeamMailbox teamMailbox) {
        MailboxQuery query = MailboxQuery.builder()
            .namespace(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())
            .user(teamMailbox.owner())
            .expression(new PrefixedWildcard(teamMailbox.mailboxName().asString()))
            .build();

        return mailboxManager.search(query, Minimal, mailboxManager.createSystemSession(teamMailbox.self()));
    }

    private Flux<ProvisionContext> listMailboxMessages(MailboxMetaData mailboxMetadata) {
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailboxMetadata.getPath().getUser());
        Username owner = ownerSession.getUser();

        return retrieveMailbox(ownerSession, mailboxMetadata)
            .flatMapMany(mailbox -> mailboxReadRightsResolver.usersHavingReadRight(owner, mailbox, ownerSession)
                .collect(ImmutableSet.toImmutableSet())
                .flatMapMany(readableUsers -> listAllMessages(mailbox, ownerSession)
                    .map(messageResult -> new ProvisionContext(readableUsers, messageResult))));
    }

    private Mono<MessageManager> retrieveMailbox(MailboxSession session, MailboxMetaData mailboxMetadata) {
        return Mono.from(mailboxManager.getMailboxReactive(mailboxMetadata.getId(), session));
    }

    private Flux<MessageResult> listAllMessages(MessageManager messageManager, MailboxSession session) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, session));
    }

}
