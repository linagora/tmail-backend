package com.linagora.tmail.webadmin.archival;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.FunctionChainer;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InboxArchivalService {
    private static final Logger LOGGER = LoggerFactory.getLogger(InboxArchivalService.class);
    private static final int LOW_CONCURRENCY = 2;
    private static final int UNLIMITED = -1;
    private static final String YEARLY_FORMAT = "yearly";
    private static final String MONTHLY_FORMAT = "monthly";

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;
    private final MailboxSessionMapperFactory mapperFactory;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final Clock clock;

    @Inject
    public InboxArchivalService(MailboxManager mailboxManager, UsersRepository usersRepository, MailboxSessionMapperFactory mapperFactory,
                                JmapSettingsRepository jmapSettingsRepository, Clock clock) {
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
        this.mapperFactory = mapperFactory;
        this.jmapSettingsRepository = jmapSettingsRepository;
        this.clock = clock;
    }

    public Mono<Task.Result> archiveInbox(InboxArchivalTask.Context context) {
        return Flux.from(usersRepository.listReactive())
            .concatMap(username -> Mono.from(jmapSettingsRepository.get(username))
                .filter(JmapSettings::inboxArchivalEnable)
                .flatMap(jmapSettings -> archiveInbox(username, jmapSettings, context))
                .defaultIfEmpty(Task.Result.COMPLETED), LOW_CONCURRENCY)
            .reduce(Task.Result.COMPLETED, Task::combine)
            .onErrorResume(e -> {
                LOGGER.error("Error while archiving INBOXes", e);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private Mono<Task.Result> archiveInbox(Username username, JmapSettings jmapSettings, InboxArchivalTask.Context context) {
        Date archiveDate = Date.from(Instant.now(clock).atZone(ZoneOffset.UTC)
            .minus(jmapSettings.inboxArchivalPeriod())
            .toInstant());
        MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

        return Mono.from(mailboxManager.getMailboxReactive(MailboxPath.inbox(username), mailboxSession))
            .map(Throwing.function(MessageManager::getMailboxEntity))
            .flatMapMany(getMailboxMessagesMetadata(mailboxSession))
            .filter(messagesOlderThanArchiveDate(archiveDate))
            .flatMap(mailboxMessage -> archiveMessage(mailboxMessage, mailboxSession, jmapSettings, context), 4)
            .then(Mono.fromCallable(() -> {
                context.increaseSuccessfulUsers();
                return Task.Result.COMPLETED;
            }))
            .onErrorResume(e -> {
                LOGGER.error("Error while archiving INBOX for user {}", username.asString(), e);
                context.increaseFailedUsers();
                context.addFailedUser(username);
                return Mono.just(Task.Result.PARTIAL);
            });
    }

    private FunctionChainer<Mailbox, Flux<MailboxMessage>> getMailboxMessagesMetadata(MailboxSession mailboxSession) {
        return Throwing.function(mailbox -> mapperFactory.getMessageMapper(mailboxSession)
            .findInMailboxReactive(mailbox, MessageRange.all(), MessageMapper.FetchType.METADATA, UNLIMITED));
    }

    private Predicate<MailboxMessage> messagesOlderThanArchiveDate(Date archiveDate) {
        return mailboxMessage -> mailboxMessage.getSaveDate()
            .orElse(mailboxMessage.getInternalDate())
            .before(archiveDate);
    }

    private Mono<Void> archiveMessage(MailboxMessage mailboxMessage, MailboxSession mailboxSession, JmapSettings jmapSettings, InboxArchivalTask.Context context) {
        return switch (jmapSettings.inboxArchivalFormat().toString()) {
            case YEARLY_FORMAT -> yearlyArchive(mailboxMessage, mailboxSession, context);
            case MONTHLY_FORMAT -> monthlyArchive(mailboxMessage, mailboxSession, context);
            default -> singleArchive(mailboxMessage, mailboxSession, context);
        };
    }

    private Mono<Void> singleArchive(MailboxMessage mailboxMessage, MailboxSession mailboxSession, InboxArchivalTask.Context context) {
        return moveMessage(mailboxMessage, mailboxSession, MailboxPath.forUser(mailboxSession.getUser(), DefaultMailboxes.ARCHIVE), context);
    }

    private Mono<Void> yearlyArchive(MailboxMessage mailboxMessage, MailboxSession mailboxSession, InboxArchivalTask.Context context) {
        int savedYear = mailboxMessage.getSaveDate()
            .orElse(mailboxMessage.getInternalDate())
            .toInstant()
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .getYear();
        MailboxPath yearlyArchiveSubMailbox = MailboxPath.forUser(mailboxSession.getUser(), String.format("Archive.%d", savedYear));

        return moveMessage(mailboxMessage, mailboxSession, yearlyArchiveSubMailbox, context);
    }

    private Mono<Void> monthlyArchive(MailboxMessage mailboxMessage, MailboxSession mailboxSession, InboxArchivalTask.Context context) {
        LocalDate savedDate = mailboxMessage.getSaveDate()
            .orElse(mailboxMessage.getInternalDate())
            .toInstant()
            .atZone(ZoneId.of("UTC"))
            .toLocalDate();
        MailboxPath monthlyArchiveSubMailbox = MailboxPath.forUser(mailboxSession.getUser(),
            String.format("Archive.%d.%d", savedDate.getYear(), savedDate.getMonth().getValue()));

        return moveMessage(mailboxMessage, mailboxSession, monthlyArchiveSubMailbox, context);
    }

    private Mono<Void> moveMessage(MailboxMessage mailboxMessage, MailboxSession mailboxSession, MailboxPath archiveMailbox, InboxArchivalTask.Context context) {
        MailboxPath inbox = MailboxPath.inbox(mailboxSession.getUser());
        return Mono.from(mailboxManager.moveMessagesReactive(MessageRange.one(mailboxMessage.getUid()), inbox, archiveMailbox, mailboxSession))
            .onErrorResume(MailboxNotFoundException.class, e -> createMailbox(mailboxSession, archiveMailbox)
                .then(Mono.from(mailboxManager.moveMessagesReactive(MessageRange.one(mailboxMessage.getUid()), inbox, archiveMailbox, mailboxSession))))
            .then(Mono.fromRunnable(() -> context.increaseArchivedMessageCount(1)))
            .doOnError(e -> {
                LOGGER.error("Error when archiving messageId {} from mailbox {} to mailbox {}", mailboxMessage.getMessageId().serialize(), inbox.asString(), archiveMailbox.asString(), e);
                context.increaseErrorMessageCount();
            })
            .then();
    }

    private Mono<Void> createMailbox(MailboxSession mailboxSession, MailboxPath archiveMailbox) {
        return Mono.from(mailboxManager.createMailboxReactive(archiveMailbox, MailboxManager.CreateOption.CREATE_SUBSCRIPTION, mailboxSession))
            .onErrorResume(MailboxExistsException.class, e -> Mono.empty())
            .then();
    }
}
