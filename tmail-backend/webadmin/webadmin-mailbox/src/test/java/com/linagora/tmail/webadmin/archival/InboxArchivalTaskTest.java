package com.linagora.tmail.webadmin.archival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

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

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

class InboxArchivalTaskTest {
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private static final Username ALICE = Username.fromLocalPartWithDomain("alice", DOMAIN);
    private static final Username ANDRE = Username.fromLocalPartWithDomain("andre", DOMAIN);
    private static final Instant NOW = Instant.parse("2023-10-04T10:15:30.00Z");
    private static final long TWO_YEARS_IN_SECOND = 63113852;
    private static final long HALF_YEARS_IN_SECOND = 15778463;
    private static final long TWO_MONTHS_IN_SECOND = 5259600;
    private static final long HALF_MONTHS_IN_SECOND = 1314900;

    private InMemoryMailboxManager mailboxManager;
    private UpdatableTickingClock saveDateClock;
    private JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils;
    private InboxArchivalTask task;

    @BeforeEach
    void setup() throws Exception {
        InMemoryIntegrationResources inMemoryIntegrationResources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = inMemoryIntegrationResources.getMailboxManager();
        saveDateClock = (UpdatableTickingClock) mailboxManager.getClock();
        DomainList domainList = mock(DomainList.class);
        Mockito.when(domainList.containsDomain(any())).thenReturn(true);
        UsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        usersRepository.addUser(ALICE, "anyPassword");
        usersRepository.addUser(ANDRE, "anyPassword");
        mailboxManager.createMailbox(MailboxPath.inbox(BOB), mailboxManager.createSystemSession(BOB));
        mailboxManager.createMailbox(MailboxPath.inbox(ALICE), mailboxManager.createSystemSession(ALICE));
        mailboxManager.createMailbox(MailboxPath.inbox(ANDRE), mailboxManager.createSystemSession(ANDRE));

        MailboxSessionMapperFactory mapperFactory = mailboxManager.getMapperFactory();
        JmapSettingsRepository jmapSettingsRepository = new MemoryJmapSettingsRepository();
        jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);
        InboxArchivalService service = new InboxArchivalService(mailboxManager, usersRepository, mapperFactory, jmapSettingsRepository, new UpdatableTickingClock(NOW));
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

    @Test
    void givenEmptyArchivalPeriodSettingThenShouldDefaultToMonthly() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void givenInvalidArchivalPeriodSettingThenShouldDefaultToMonthly() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.period", "invalid"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void givenSingleArchivalFormatSettingThenShouldCreateArchiveMailboxIfNeededAndArchive() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.format", "single"));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void givenYearlyArchivalFormatSettingThenShouldCreateYearlyArchiveMailboxesIfNeededAndArchive() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.format", "yearly"));

        saveDateClock.setInstant(Instant.parse("2013-10-04T10:15:30.00Z"));
        appendInboxMessage(BOB);
        saveDateClock.setInstant(Instant.parse("2014-10-04T10:15:30.00Z"));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive.2013"), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive.2014"), 1L);
    }

    @Test
    void givenMonthlyArchivalFormatSettingThenShouldCreateMonthlyArchiveMailboxesIfNeededAndArchive() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.format", "monthly"));

        saveDateClock.setInstant(Instant.parse("2014-01-04T10:15:30.00Z"));
        appendInboxMessage(BOB);
        saveDateClock.setInstant(Instant.parse("2014-02-04T10:15:30.00Z"));
        appendInboxMessage(BOB);
        saveDateClock.setInstant(Instant.parse("2015-12-04T10:15:30.00Z"));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive.2014.1"), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive.2014.2"), 1L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive.2015.12"), 1L);
    }

    @Test
    void givenEmptyArchivalFormatSettingThenShouldArchiveInSingleArchiveMailboxByDefault() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void givenInvalidArchivalFormatSettingThenShouldArchiveInSingleArchiveMailboxByDefault() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.format", "invalid"));
        mailboxManager.createMailbox(MailboxPath.forUser(BOB, "Archive"), mailboxManager.createSystemSession(BOB));

        saveDateClock.setInstant(NOW.minusSeconds(TWO_MONTHS_IN_SECOND));
        appendInboxMessage(BOB);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive"), 1L);
    }

    @Test
    void shouldArchivePerUserSetting() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.format", "monthly"));
        setJmapSettings(ALICE, Map.of("inbox.archival.enabled", "true",
            "inbox.archival.format", "yearly"));
        setJmapSettings(ANDRE, Map.of("inbox.archival.enabled", "false"));

        saveDateClock.setInstant(Instant.parse("2014-01-04T10:15:30.00Z"));
        appendInboxMessage(BOB);
        saveDateClock.setInstant(Instant.parse("2014-02-04T10:15:30.00Z"));
        appendInboxMessage(ALICE);
        saveDateClock.setInstant(Instant.parse("2014-02-04T10:15:30.00Z"));
        appendInboxMessage(ANDRE);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertMessagesCount(MailboxPath.inbox(BOB), 0L);
        assertMessagesCount(MailboxPath.forUser(BOB, "Archive.2014.1"), 1L);
        assertMessagesCount(MailboxPath.inbox(ALICE), 0L);
        assertMessagesCount(MailboxPath.forUser(ALICE, "Archive.2014"), 1L);
        assertMessagesCount(MailboxPath.inbox(ANDRE), 1L);
    }

    @Test
    void additionalInformationShouldReturnEmptyWhenNoArchivableMessages() {
        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(InboxArchivalTask.Context.Snapshot.builder()
                .archivedMessageCount(0)
                .errorMessageCount(0)
                .build());
    }

    @Test
    void additionalInformationShouldCountArchivedMessages() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true"));
        setJmapSettings(ALICE, Map.of("inbox.archival.enabled", "true"));

        saveDateClock.setInstant(Instant.parse("2014-01-04T10:15:30.00Z"));
        appendInboxMessage(BOB);
        saveDateClock.setInstant(Instant.parse("2014-02-04T10:15:30.00Z"));
        appendInboxMessage(ALICE);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(InboxArchivalTask.Context.Snapshot.builder()
                .archivedMessageCount(2)
                .errorMessageCount(0)
                .build());
    }

    @Test
    void additionalInformationShouldNotCountNotArchivedMessages() throws MailboxException {
        setJmapSettings(BOB, Map.of("inbox.archival.enabled", "true"));
        setJmapSettings(ALICE, Map.of("inbox.archival.enabled", "false"));

        saveDateClock.setInstant(Instant.parse("2014-01-04T10:15:30.00Z"));
        appendInboxMessage(BOB);
        saveDateClock.setInstant(Instant.parse("2014-02-04T10:15:30.00Z"));
        appendInboxMessage(ALICE);

        Task.Result result = task.run();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(task.snapshot())
            .isEqualTo(InboxArchivalTask.Context.Snapshot.builder()
                .archivedMessageCount(1)
                .errorMessageCount(0)
                .build());
    }

    private void setJmapSettings(Username username, Map<String, String> settings) {
        jmapSettingsRepositoryUtils.reset(username, settings);
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
