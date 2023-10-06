package com.linagora.tmail.james.app;

import static org.apache.james.JamesServerMain.LOGGER;
import static org.apache.james.MemoryJamesServerMain.JMAP;
import static org.apache.james.MemoryJamesServerMain.WEBADMIN;

import java.util.List;

import org.apache.james.ExtraProperties;
import org.apache.james.FakeSearchMailboxModule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.jmap.draft.JMAPListenerModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.BlobMemoryModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.data.MemoryDataModule;
import org.apache.james.modules.data.MemoryDelegationStoreModule;
import org.apache.james.modules.data.MemoryUsersRepositoryModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.MemoryMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.memory.MemoryMailQueueModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.vault.DeletedMessageVaultModule;
import org.apache.james.rate.limiter.memory.MemoryRateLimiterModule;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.KeystoreMemoryModule;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.jmap.contact.MemoryEmailAddressContactModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseCommonModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.MemoryFirebaseSubscriptionRepository;
import com.linagora.tmail.james.jmap.label.MemoryLabelRepositoryModule;
import com.linagora.tmail.james.jmap.method.CalendarEventMethodModule;
import com.linagora.tmail.james.jmap.method.ContactAutocompleteMethodModule;
import com.linagora.tmail.james.jmap.method.CustomMethodModule;
import com.linagora.tmail.james.jmap.method.EmailRecoveryActionMethodModule;
import com.linagora.tmail.james.jmap.method.EmailSendMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterSetMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardGetMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardSetMethodModule;
import com.linagora.tmail.james.jmap.method.JmapSettingsMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.james.jmap.method.LabelMethodModule;
import com.linagora.tmail.james.jmap.oidc.WebFingerModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepositoryModule;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxJmapModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingModule;
import com.linagora.tmail.team.TMailScanningQuotaSearcherModule;
import com.linagora.tmail.team.TeamMailboxModule;
import com.linagora.tmail.webadmin.EmailAddressContactRoutesModule;
import com.linagora.tmail.webadmin.RateLimitPlanRoutesModule;
import com.linagora.tmail.webadmin.TeamMailboxRoutesModule;
import com.linagora.tmail.webadmin.archival.InboxArchivalTaskModule;
import com.linagora.tmail.webadmin.cleanup.MailboxesCleanupModule;

public class MemoryServer {
    public static final Module IN_MEMORY_SERVER_MODULE = Modules.combine(
        new MailetProcessingModule(),
        new BlobMemoryModule(),
        new DeletedMessageVaultModule(),
        new BlobExportMechanismModule(),
        new MailboxModule(),
        new MemoryDataModule(),
        new MemoryDelegationStoreModule(),
        new MemoryEventStoreModule(),
        new MemoryMailboxModule(),
        new MemoryMailQueueModule(),
        new TaskManagerModule());

    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new ManageSieveServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    public static final Module JMAP_LINAGORA = Modules.override(
        JMAP,
        new CalendarEventMethodModule(),
        new ContactAutocompleteMethodModule(),
        new CustomMethodModule(),
        new EncryptedEmailDetailedViewGetMethodModule(),
        new EncryptedEmailFastViewGetMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new ForwardGetMethodModule(),
        new InMemoryEncryptedEmailContentStoreModule(),
        new KeystoreMemoryModule(),
        new ForwardSetMethodModule(),
        new KeystoreSetMethodModule(),
        new KeystoreGetMethodModule(),
        new TicketRoutesModule(),
        new WebFingerModule(),
        new EmailRecoveryActionMethodModule(),
        new LabelMethodModule(),
        new JmapSettingsMethodModule(),
        new MailboxesCleanupModule(),
        new InboxArchivalTaskModule())
        .with(new TeamMailboxJmapModule());

    public static final Module MODULES = Modules.override(
          IN_MEMORY_SERVER_MODULE,
          PROTOCOLS,
          JMAP_LINAGORA,
          WEBADMIN,
          new TeamMailboxModule(),
          new TeamMailboxRoutesModule(),
          new DKIMMailetModule())
        .with(new TeamMailboxModule(),
            new TMailScanningQuotaSearcherModule(),
            new MemoryRateLimiterModule(),
            new MemoryRateLimitingModule(),
            new RateLimitPlanRoutesModule(),
            new MemoryEmailAddressContactModule(),
            new EmailAddressContactRoutesModule(),
            new MemoryLabelRepositoryModule(),
            new MemoryJmapSettingsRepositoryModule());

    public static void main(String[] args) throws Exception {
        MemoryConfiguration configuration = MemoryConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new FakeSearchMailboxModule(), new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(MemoryConfiguration configuration) {
        ExtraProperties.initialize();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .combineWith(new UsersRepositoryModuleChooser(new MemoryUsersRepositoryModule())
                .chooseModules(configuration.usersRepositoryImplementation()))
            .combineWith(chooseFirebase(configuration.firebaseModuleChooserConfiguration()))
            .combineWith(chooseLinagoraServiceDiscovery(configuration.linagoraServicesDiscoveryModuleChooserConfiguration()))
            .overrideWith(chooseMailbox(configuration.mailboxConfiguration()))
            .overrideWith(chooseJmapModule(configuration));
    }

    private static Module chooseJmapModule(MemoryConfiguration configuration) {
        if (configuration.jmapEnabled()) {
            return new JMAPListenerModule();
        }
        return binder -> {
        };
    }

    private static class EncryptedMailboxModule extends AbstractModule {
        @Provides
        @Singleton
        MailboxManager provide(InMemoryMailboxManager mailboxManager, KeystoreManager keystoreManager,
                               ClearEmailContentFactory clearEmailContentFactory,
                               InMemoryEncryptedEmailContentStore contentStore) {
            return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
        }
    }

    private static List<Module> chooseMailbox(MailboxConfiguration mailboxConfiguration) {
        if (mailboxConfiguration.isEncryptionEnabled()) {
            return ImmutableList.of(new EncryptedMailboxModule());
        }
        return ImmutableList.of();
    }

    private static List<Module> chooseFirebase(FirebaseModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new MemoryFirebaseSubscriptionRepository.Module(), new FirebaseCommonModule());
        }
        return List.of();
    }

    private static List<Module> chooseLinagoraServiceDiscovery(LinagoraServicesDiscoveryModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new LinagoraServicesDiscoveryModule());
        }
        return List.of();
    }
}
