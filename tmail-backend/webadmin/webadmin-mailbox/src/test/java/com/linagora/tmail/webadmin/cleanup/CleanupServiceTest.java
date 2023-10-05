package com.linagora.tmail.webadmin.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

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
import org.apache.james.mailbox.SessionProvider;
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
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import reactor.core.publisher.Flux;

public class CleanupServiceTest {
    private static final Domain DOMAIN = Domain.of("example.org");
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    private CleanupService cleanupService;
    private JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils;
    private SessionProvider sessionProvider;
    private SystemMailboxesProvider systemMailboxesProvider;
    private UpdatableTickingClock clock;
    private MailboxSession bobMailboxSession;
    private MessageManager bobMessageManager;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        StoreMailboxManager storeMailboxManager = resources.getMailboxManager();
        sessionProvider = storeMailboxManager.getSessionProvider();
        systemMailboxesProvider = new SystemMailboxesProviderImpl(storeMailboxManager);
        bobMailboxSession = sessionProvider.createSystemSession(BOB);
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(BOB, "anyPassword");
        storeMailboxManager.createMailbox(MailboxPath.forUser(BOB,
                DefaultMailboxes.TRASH),
            bobMailboxSession);
        bobMessageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        JmapSettingsRepository jmapSettingsRepository = new MemoryJmapSettingsRepository();
        jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);
        clock = new UpdatableTickingClock(Instant.now());
        cleanupService = new CleanupService(usersRepository, jmapSettingsRepository, sessionProvider, systemMailboxesProvider, clock);
    }

    @Test
    void cleanupTrashShouldRemoveMessageWhenMessageIsExpiredAndPeriodSettingIsWeekly() throws Exception {
        jmapSettingsRepositoryUtils.reset(BOB, Map.of("trash.cleanup.enabled", "true",
            "trash.cleanup.period", "weekly"));

        bobMessageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
                new Date(),
            bobMailboxSession,
                false,
                new Flags());

        clock.setInstant(clock.instant().plus(8, ChronoUnit.DAYS));

        assertThat(cleanupService.cleanup(Role.TRASH, RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(bobMessageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, bobMailboxSession))
                .collect(ImmutableList.toImmutableList())
                .block())
            .hasSize(0);
    }

    @Test
    void cleanupTrashShouldKeepMessageWhenMessageIsNotExpiredAndPeriodSettingIsWeekly() throws Exception {
        jmapSettingsRepositoryUtils.reset(BOB, Map.of("trash.cleanup.enabled", "true",
            "trash.cleanup.period", "weekly"));

        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            bobMailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(5, ChronoUnit.DAYS));

        assertThat(cleanupService.cleanup(Role.TRASH, RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, bobMailboxSession))
                .collect(ImmutableList.toImmutableList())
                .block())
            .hasSize(1);
    }

    @Test
    void cleanupTrashShouldRemoveMessageWhenMessageIsExpiredAndPeriodSettingIsMonthly() throws Exception {
        jmapSettingsRepositoryUtils.reset(BOB, Map.of("trash.cleanup.enabled", "true",
            "trash.cleanup.period", "monthly"));

        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            bobMailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(45, ChronoUnit.DAYS));

        assertThat(cleanupService.cleanup(Role.TRASH, RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, bobMailboxSession))
            .collect(ImmutableList.toImmutableList())
            .block())
            .hasSize(0);
    }

    @Test
    void cleanupTrashShouldKeepMessageWhenMessageIsNotExpiredAndPeriodSettingIsMonthly() throws Exception {
        jmapSettingsRepositoryUtils.reset(BOB, Map.of("trash.cleanup.enabled", "true",
            "trash.cleanup.period", "monthly"));

        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            bobMailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(15, ChronoUnit.DAYS));

        assertThat(cleanupService.cleanup(Role.TRASH, RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, bobMailboxSession))
            .collect(ImmutableList.toImmutableList())
            .block())
            .hasSize(1);
    }

    @Test
    void cleanupTrashShouldKeepMessageWhenTrashCleanupEnabledSettingIsFalse() throws Exception {
        jmapSettingsRepositoryUtils.reset(BOB, Map.of("trash.cleanup.enabled", "false",
            "trash.cleanup.period", "weekly"));

        MessageManager messageManager = systemMailboxesProvider.findMailbox(Role.TRASH, BOB);
        messageManager.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()),
            new Date(),
            bobMailboxSession,
            false,
            new Flags());

        clock.setInstant(clock.instant().plus(8, ChronoUnit.DAYS));

        assertThat(cleanupService.cleanup(Role.TRASH, RunningOptions.DEFAULT, new CleanupContext()).block()).isEqualTo(Task.Result.COMPLETED);
        assertThat(Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, bobMailboxSession))
                .collect(ImmutableList.toImmutableList())
                .block())
            .hasSize(1);
    }
}
