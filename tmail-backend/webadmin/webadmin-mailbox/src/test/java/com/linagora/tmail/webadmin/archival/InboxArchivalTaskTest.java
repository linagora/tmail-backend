package com.linagora.tmail.webadmin.archival;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

import javax.mail.Flags;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.linagora.tmail.james.jmap.settings.JmapSettingsKey;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUpsertRequest;
import com.linagora.tmail.james.jmap.settings.JmapSettingsValue;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;

class InboxArchivalTaskTest {
    private static final String INBOX_MAILBOX_NAME = "INBOX";
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final MailboxPath BOB_INBOX_MAILBOX = MailboxPath.forUser(BOB, INBOX_MAILBOX_NAME);
    private static final MailboxPath ALICE_INBOX_MAILBOX = MailboxPath.forUser(ALICE, INBOX_MAILBOX_NAME);
    private static final Instant NOW = ZonedDateTime.now().toInstant();
    private static final long TWO_YEARS_IN_SECOND = 63113852;
    private static final long HALF_YEARS_IN_SECOND = 15778463;
    private static final long TWO_MONTHS_IN_SECOND = 5259600;
    private static final long HALF_MONTHS_IN_SECOND = 1314900;

    private InMemoryMailboxManager mailboxManager;
    private UsersRepository usersRepository;
    private Clock clock;
    private UpdatableTickingClock saveDateClock;
    private JmapSettingsRepository jmapSettingsRepository;
    private InboxArchivalTask task;

    @BeforeEach
    void setup() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = inMemoryIntegrationResources.getMailboxManager();
        saveDateClock = (UpdatableTickingClock) mailboxManager.getClock();
        DomainList domainList = mock(DomainList.class);
        Mockito.when(domainList.containsDomain(any())).thenReturn(true);
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        usersRepository.addUser(ALICE, "anyPassword");
        mailboxManager.createMailbox(BOB_INBOX_MAILBOX, mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(ALICE_INBOX_MAILBOX, mailboxManager.createSystemSession(ALICE));

        clock = new UpdatableTickingClock(NOW);
        MailboxSessionMapperFactory mapperFactory = mailboxManager.getMapperFactory();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        InboxArchivalService service = new InboxArchivalService(mailboxManager, usersRepository, mapperFactory, jmapSettingsRepository, clock);
        task = new InboxArchivalTask(service);
    }

    @Test
    void shouldArchiveUserInboxWhenArchivalEnabled() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_YEARS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void shouldNotArchiveUserInboxWhenArchivalDisabled() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "false"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_YEARS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 0L);
    }

    @Test
    void shouldNotArchiveUserInboxWhenEmptyArchivalSetting() throws MailboxException {
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_YEARS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 0L);
    }

    @Test
    void shouldNotArchiveUserInboxWhenInvalidArchivalEnableSetting() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "invalid"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_YEARS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 0L);
    }

    // test for inbox.archival.period
    @Test
    void givenMonthlyPeriodThenShouldArchiveMessagesOlderThanOneMonth() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.period", "monthly"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void givenYearlyPeriodThenShouldArchiveMessagesOlderThanOneYear() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.period", "yearly"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_YEARS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void givenMonthlyPeriodThenShouldNotArchiveMessagesYoungerThanOneMonth() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.period", "monthly"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(HALF_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 0L);
    }

    @Test
    void givenYearlyPeriodThenShouldNotArchiveMessagesYoungerThanOneYear() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.period", "yearly"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(HALF_YEARS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 0L);
    }

    // TODO should fallback to default period (monthly) if no period specified
    // TODO should fallback to default period (monthly) if invalid period specified

    // test for inbox.archival.format
    // TODO should create archive mailbox upon archive mailboxes missing (yearly, monthly, single format cases)
    // TODO should archive in Archive mailbox upon single case
    // TODO should archive in e.g. Archive.2023 submailbox upon yearly case
    // TODO should archive in e.g. Archive.2023.October submailbox upon monthly case
    // TODO should fallback to default format (single) if no format specified
    // TODO should fallback to default format (single) if invalid format specified

    // other test cases
    // TODO should archive multiples messages in a INBOX
    // TODO should archive all users INBOXes (with perUser period/format)
    // TODO should not archive user INBOX that disable archival


    private void setJmapSettings(Username username, Map<String, String> settings) {
        Map<JmapSettingsKey, JmapSettingsValue> javaSettings = settings
            .entrySet()
            .stream()
            .map(entry -> entry(JmapSettingsKey.liftOrThrow(entry.getKey()), new JmapSettingsValue(entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Mono.from(jmapSettingsRepository.reset(username,
                new JmapSettingsUpsertRequest(scala.collection.immutable.Map.from(JavaConverters.asScala(javaSettings)))))
            .block();
    }

    private void appendInboxMessage(Username username) throws MailboxException {
        appendMessage(MailboxPath.inbox(username));
    }

    private void appendMessage(MailboxPath mailboxPath) throws MailboxException {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());
        mailboxManager.getMailbox(mailboxPath, session)
            .appendMessage(new ByteArrayInputStream(String.format("random content %4.3f", Math.random()).getBytes()),
                Date.from(NOW),
                session,
                true,
                new Flags());
    }

    private void assertMessagesCount(MailboxPath mailboxPath, long messagesCount) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(mailboxPath.getUser());
        assertThat(mailboxManager.getMailbox(mailboxPath, mailboxSession).getMessageCount(mailboxSession))
            .isEqualTo(messagesCount);
    }
}
