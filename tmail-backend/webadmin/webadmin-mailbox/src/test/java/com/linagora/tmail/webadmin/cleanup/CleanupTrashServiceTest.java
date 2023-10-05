package com.linagora.tmail.webadmin.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.task.Task;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUpsertRequest;
import com.linagora.tmail.james.jmap.settings.JmapSettingsValue;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;

public class CleanupTrashServiceTest {
    private static final Domain DOMAIN = Domain.of("example.org");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private CleanupTrashService cleanupTrashService;
    private JmapSettingsRepository jmapSettingsRepository;
    private StoreMailboxManager storeMailboxManager;
    private SystemMailboxesProvider systemMailboxesProvider;
    private UpdatableTickingClock clock;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        storeMailboxManager = resources.getMailboxManager();
        systemMailboxesProvider = new SystemMailboxesProviderImpl(storeMailboxManager);
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        clock = new UpdatableTickingClock(Instant.now());
        cleanupTrashService = new CleanupTrashService(usersRepository, jmapSettingsRepository, storeMailboxManager, systemMailboxesProvider, clock);
    }

    @Test
    void cleanupTrashShouldRemoveMessageWhenMessageIsExpiredAndPeriodSettingIsWeekly() throws Exception {
        Mono.from(jmapSettingsRepository.reset(BOB,
            new JmapSettingsUpsertRequest(Map.from(JavaConverters.asScala(ImmutableMap.of(
                JmapSettings.trashCleanupEnabledSetting(),
                new JmapSettingsValue("true"),
                JmapSettings.trashCleanupPeriodSetting(),
                new JmapSettingsValue(JmapSettings.weeklyPeriod()))
            ))))).block();

        MailboxSession mailboxSession = storeMailboxManager.getSessionProvider().createSystemSession(BOB);
        storeMailboxManager.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH), mailboxSession);
        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
                new Date(),
                mailboxSession,
                false,
                new Flags());

        clock.setInstant(clock.instant().plus(8, ChronoUnit.DAYS));

        assertThat(cleanupTrashService.cleanupTrash(RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession))
                .collect(ImmutableList.toImmutableList())
                .block())
            .hasSize(0);
    }

    @Test
    void cleanupTrashShouldKeepMessageWhenMessageIsNotExpiredAndPeriodSettingIsWeekly() throws Exception {
        Mono.from(jmapSettingsRepository.reset(BOB,
            new JmapSettingsUpsertRequest(Map.from(JavaConverters.asScala(ImmutableMap.of(
                JmapSettings.trashCleanupEnabledSetting(),
                new JmapSettingsValue("true"),
                JmapSettings.trashCleanupPeriodSetting(),
                new JmapSettingsValue(JmapSettings.weeklyPeriod()))
            ))))).block();

        MailboxSession mailboxSession = storeMailboxManager.getSessionProvider().createSystemSession(BOB);
        storeMailboxManager.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH), mailboxSession);
        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            mailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(5, ChronoUnit.DAYS));

        assertThat(cleanupTrashService.cleanupTrash(RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession))
                .collect(ImmutableList.toImmutableList())
                .block())
            .hasSize(1);
    }

    @Test
    void cleanupTrashShouldRemoveMessageWhenMessageIsExpiredAndPeriodSettingIsMonthly() throws Exception {
        Mono.from(jmapSettingsRepository.reset(BOB,
            new JmapSettingsUpsertRequest(Map.from(JavaConverters.asScala(ImmutableMap.of(
                JmapSettings.trashCleanupEnabledSetting(),
                new JmapSettingsValue("true"),
                JmapSettings.trashCleanupPeriodSetting(),
                new JmapSettingsValue(JmapSettings.monthlyPeriod()))
            ))))).block();

        MailboxSession mailboxSession = storeMailboxManager.getSessionProvider().createSystemSession(BOB);
        storeMailboxManager.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH), mailboxSession);
        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            mailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(45, ChronoUnit.DAYS));

        assertThat(cleanupTrashService.cleanupTrash(RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession))
            .collect(ImmutableList.toImmutableList())
            .block())
            .hasSize(0);
    }

    @Test
    void cleanupTrashShouldKeepMessageWhenMessageIsNotExpiredAndPeriodSettingIsMonthly() throws Exception {
        Mono.from(jmapSettingsRepository.reset(BOB,
            new JmapSettingsUpsertRequest(Map.from(JavaConverters.asScala(ImmutableMap.of(
                JmapSettings.trashCleanupEnabledSetting(),
                new JmapSettingsValue("true"),
                JmapSettings.trashCleanupPeriodSetting(),
                new JmapSettingsValue(JmapSettings.monthlyPeriod()))
            ))))).block();

        MailboxSession mailboxSession = storeMailboxManager.getSessionProvider().createSystemSession(BOB);
        storeMailboxManager.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH), mailboxSession);
        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            mailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(15, ChronoUnit.DAYS));

        assertThat(cleanupTrashService.cleanupTrash(RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession))
            .collect(ImmutableList.toImmutableList())
            .block())
            .hasSize(1);
    }

    @Test
    void cleanupTrashShouldKeepMessageWhenTrashCleanupEnabledSettingIsFalse() throws Exception {
        Mono.from(jmapSettingsRepository.reset(BOB,
            new JmapSettingsUpsertRequest(Map.from(JavaConverters.asScala(ImmutableMap.of(
                JmapSettings.trashCleanupEnabledSetting(),
                new JmapSettingsValue("false"),
                JmapSettings.trashCleanupPeriodSetting(),
                new JmapSettingsValue(JmapSettings.weeklyPeriod()))
            ))))).block();

        MailboxSession mailboxSession = storeMailboxManager.getSessionProvider().createSystemSession(BOB);
        storeMailboxManager.createMailbox(MailboxPath.forUser(BOB, DefaultMailboxes.TRASH), mailboxSession);
        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            mailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(8, ChronoUnit.DAYS));

        assertThat(cleanupTrashService.cleanupTrash(RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession))
                .collect(ImmutableList.toImmutableList())
                .block())
            .hasSize(1);
    }
}
