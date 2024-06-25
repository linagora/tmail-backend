package com.linagora.tmail.james.app;

import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.RABBITMQ;
import static org.apache.james.PostgresJamesServerMain.JMAP;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.eventsourcing.eventstore.EventNestedTypes;
import org.apache.james.jmap.JMAPListenerModule;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.DistributedTaskSerializationModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.data.PostgresDLPConfigurationStoreModule;
import org.apache.james.modules.data.PostgresDataModule;
import org.apache.james.modules.data.PostgresDelegationStoreModule;
import org.apache.james.modules.data.PostgresEventStoreModule;
import org.apache.james.modules.data.PostgresUsersRepositoryModule;
import org.apache.james.modules.data.PostgresVacationModule;
import org.apache.james.modules.data.SievePostgresRepositoryModules;
import org.apache.james.modules.event.JMAPEventBusModule;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.events.PostgresDeadLetterModule;
import org.apache.james.modules.mailbox.DefaultEventModule;
import org.apache.james.modules.mailbox.OpenSearchClientModule;
import org.apache.james.modules.mailbox.OpenSearchDisabledModule;
import org.apache.james.modules.mailbox.OpenSearchMailboxModule;
import org.apache.james.modules.mailbox.PostgresDeletedMessageVaultModule;
import org.apache.james.modules.mailbox.PostgresMailboxModule;
import org.apache.james.modules.mailbox.RLSSupportPostgresMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.plugins.QuotaMailingModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.activemq.ActiveMQQueueModule;
import org.apache.james.modules.queue.rabbitmq.FakeMailQueueViewModule;
import org.apache.james.modules.queue.rabbitmq.RabbitMQMailQueueModule;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DLPRoutesModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.InconsistencyQuotasSolvingRoutesModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.JmapTasksModule;
import org.apache.james.modules.server.JmapUploadCleanupModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MailboxesExportRoutesModule;
import org.apache.james.modules.server.MessagesRoutesModule;
import org.apache.james.modules.server.RabbitMailQueueRoutesModule;
import org.apache.james.modules.server.ReIndexingModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.UserIdentityModule;
import org.apache.james.modules.server.WebAdminMailOverWebModule;
import org.apache.james.modules.server.WebAdminReIndexingTaskSerializationModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.task.DistributedTaskManagerModule;
import org.apache.james.modules.task.PostgresTaskExecutionDetailsProjectionGuiceModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.quota.search.scanning.ScanningQuotaSearcher;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.linagora.tmail.DatabaseCombinedUserRequireModule;
import com.linagora.tmail.ScheduledReconnectionHandler;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.blob.blobid.list.BlobStoreModulesChooser;
import com.linagora.tmail.contact.RabbitMQEmailAddressContactModule;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.postgres.PostgresEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.postgres.PostgresEncryptedMailboxModule;
import com.linagora.tmail.encrypted.postgres.PostgresKeystoreModule;
import com.linagora.tmail.event.DistributedEmailAddressContactEventModule;
import com.linagora.tmail.healthcheck.TasksHeathCheckModule;
import com.linagora.tmail.james.jmap.TMailJMAPModule;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.MemoryEmailAddressContactModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseCommonModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.PostgresFirebaseRepositoryModule;
import com.linagora.tmail.james.jmap.label.PostgresLabelRepositoryModule;
import com.linagora.tmail.james.jmap.mail.TMailMailboxSortOrderProviderModule;
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
import com.linagora.tmail.james.jmap.module.OSContactAutoCompleteModule;
import com.linagora.tmail.james.jmap.oidc.WebFingerModule;
import com.linagora.tmail.james.jmap.publicAsset.PostgresPublicAssetRepositoryModule;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetsModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsRepositoryModule;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxJmapModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;
import com.linagora.tmail.rate.limiter.api.postgres.module.PostgresRateLimitingModule;
import com.linagora.tmail.rspamd.RspamdModule;
import com.linagora.tmail.team.TeamMailboxModule;
import com.linagora.tmail.webadmin.EmailAddressContactRoutesModule;
import com.linagora.tmail.webadmin.RateLimitPlanRoutesModule;
import com.linagora.tmail.webadmin.TeamMailboxRoutesModule;
import com.linagora.tmail.webadmin.archival.InboxArchivalTaskModule;
import com.linagora.tmail.webadmin.cleanup.MailboxesCleanupModule;

public class PostgresTmailServer {
    static final Logger LOGGER = LoggerFactory.getLogger("org.apache.james.CONFIGURATION");

    private static final Module EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE = binder ->
        binder.bind(new TypeLiteral<Set<DTOModule<?, ? extends DTO>>>() {
            }).annotatedWith(Names.named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME))
            .toInstance(Set.of());

    public static void main(String[] args) throws Exception {
        ExtraProperties.initialize();

        PostgresTmailConfiguration configuration = PostgresTmailConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new JMXServerModule())
            .overrideWith(new RunArgumentsModule(args));

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(PostgresTmailConfiguration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(POSTGRES_MODULE_AGGREGATE.apply(configuration))
            .combineWith(chooseUserRepositoryModule(configuration))
            .combineWith(chooseBlobStoreModules(configuration))
            .combineWith(chooseRedisRateLimiterModule(configuration))
            .combineWith(chooseRspamdModule(configuration))
            .combineWith(chooseLinagoraServicesDiscovery(configuration.linagoraServicesDiscoveryModuleChooserConfiguration()))
            .combineWith(chooseFirebase(configuration.firebaseModuleChooserConfiguration()))
            .combineWith(chooseRLSSupportPostgresMailboxModule(configuration))
            .overrideWith(chooseSearchModules(configuration))
            .overrideWith(chooseMailbox(configuration.mailboxConfiguration()))
            .overrideWith(chooseJmapModule(configuration))
            .overrideWith(chooseTaskManagerModules(configuration));
    }

    private static final Module WEBADMIN = Modules.combine(
        new DataRoutesModules(),
        new DeletedMessageVaultRoutesModule(),
        new DLPRoutesModule(),
        new EmailAddressContactRoutesModule(),
        new InconsistencyQuotasSolvingRoutesModule(),
        new InboxArchivalTaskModule(),
        new JmapUploadCleanupModule(),
        new JmapTasksModule(),
        new MailboxRoutesModule(),
        new MailboxesCleanupModule(),
        new MailboxesExportRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new MessagesRoutesModule(),
        new RateLimitPlanRoutesModule(),
        new ReIndexingModule(),
        new SieveRoutesModule(),
        new TeamMailboxModule(),
        new TeamMailboxRoutesModule(),
        new UserIdentityModule(),
        new WebAdminMailOverWebModule(),
        new WebAdminReIndexingTaskSerializationModule(),
        new WebAdminServerModule());

    public static final Module JMAP_LINAGORA = Modules.override(
        JMAP,
        new TMailJMAPModule(),
        new CalendarEventMethodModule(),
        new ContactAutocompleteMethodModule(),
        new CustomMethodModule(),
        new EncryptedEmailDetailedViewGetMethodModule(),
        new EncryptedEmailFastViewGetMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new ForwardGetMethodModule(),
        new PostgresEncryptedEmailContentStoreModule(),
        new PostgresKeystoreModule(),
        new ForwardSetMethodModule(),
        new KeystoreSetMethodModule(),
        new KeystoreGetMethodModule(),
        new TicketRoutesModule(),
        new WebFingerModule(),
        new EmailRecoveryActionMethodModule(),
        new LabelMethodModule(),
        new JmapSettingsMethodModule(),
        new PublicAssetsModule())
        .with(new TeamMailboxJmapModule());

    private static final Module PROTOCOLS = Modules.combine(
        JMAP_LINAGORA,
        new IMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        WEBADMIN);

    private static final Module POSTGRES_SERVER_MODULE = Modules.combine(
        new BlobExportMechanismModule(),
        new PostgresDelegationStoreModule(),
        new PostgresMailboxModule(),
        new PostgresDeadLetterModule(),
        new PostgresDataModule(),
        new MailboxModule(),
        new SievePostgresRepositoryModules(),
        new TaskManagerModule(),
        new TikaMailboxModule(),
        new PostgresVacationModule(),
        new PostgresDLPConfigurationStoreModule(),
        new PostgresDeletedMessageVaultModule(),
        new PostgresEventStoreModule(),
        EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE);

    public static final Module PLUGINS = new QuotaMailingModule();

    public static final Function<PostgresTmailConfiguration, Module> POSTGRES_MODULE_AGGREGATE = configuration -> Modules
        .override(Modules.combine(
            new MailetProcessingModule(),
            new DKIMMailetModule(),
            POSTGRES_SERVER_MODULE,
            PROTOCOLS,
            PLUGINS))
        .with(new TeamMailboxModule(),
            new TMailMailboxSortOrderProviderModule(),
            new PostgresRateLimitingModule(),
            new RateLimitPlanRoutesModule(),
            new EmailAddressContactRoutesModule(),
            new PostgresLabelRepositoryModule(),
            new PostgresJmapSettingsRepositoryModule(),
            new PostgresPublicAssetRepositoryModule(),
            new TasksHeathCheckModule(),
            chooseEventBusModules(configuration));

    private static final Module SCANNING_QUOTA_SEARCH_MODULE = new AbstractModule() {
        @Override
        protected void configure() {
            bind(ScanningQuotaSearcher.class).in(Scopes.SINGLETON);
            bind(QuotaSearcher.class).to(ScanningQuotaSearcher.class);
        }
    };

    private static final Module SCANNING_SEARCH_MODULE = new AbstractModule() {
        @Override
        protected void configure() {
            bind(MessageSearchIndex.class).to(SimpleMessageSearchIndex.class);
            bind(FakeMessageSearchIndex.class).in(Scopes.SINGLETON);
            bind(ListeningMessageSearchIndex.class).to(FakeMessageSearchIndex.class);
        }
    };

    private static Module chooseBlobStoreModules(PostgresTmailConfiguration configuration) {
        return Modules.combine(Modules.combine(BlobStoreModulesChooser.chooseModules(configuration.blobStoreConfiguration(),
                BlobStoreModulesChooser.SingleSaveDeclarationModule.BackedStorage.POSTGRES)),
            new BlobStoreCacheModulesChooser.CacheDisabledModule());
    }

    private static final Module IN_MEMORY_EVENT_BUS_FEATURE_MODULE = Modules.combine(
        new MemoryEmailAddressContactModule()
    );

    private static final Module RABBITMQ_EVENT_BUS_FEATURE_MODULE = Modules.combine(
        new DistributedEmailAddressContactEventModule(),
        new PostgresEmailAddressContactEventDeadLettersModule());

    public static Module chooseEventBusModules(PostgresTmailConfiguration configuration) {
        return switch (configuration.eventBusImpl()) {
            case IN_MEMORY -> Modules.combine(new DefaultEventModule(),
                new ActiveMQQueueModule(),
                IN_MEMORY_EVENT_BUS_FEATURE_MODULE);
            case RABBITMQ -> Modules.combine(new RabbitMQModule(),
                new RabbitMQMailQueueModule(),
                new FakeMailQueueViewModule(),
                new RabbitMailQueueRoutesModule(),
                new RabbitMQEmailAddressContactModule(),
                new ScheduledReconnectionHandler.Module(),
                Modules.override(new DefaultEventModule())
                    .with(new RabbitMQEventBusModule()),
                new DistributedTaskSerializationModule(),
                RABBITMQ_EVENT_BUS_FEATURE_MODULE);
        };
    }

    public static List<Module> chooseTaskManagerModules(PostgresTmailConfiguration configuration) {
        switch (configuration.eventBusImpl()) {
            case IN_MEMORY:
                return List.of(new TaskManagerModule(), new PostgresTaskExecutionDetailsProjectionGuiceModule());
            case RABBITMQ:
                return List.of(new DistributedTaskManagerModule());
            default:
                throw new RuntimeException("Unsupported event-bus implementation " + configuration.eventBusImpl().name());
        }
    }

    public static Module chooseUserRepositoryModule(PostgresTmailConfiguration configuration) {
        return Modules.combine(PostgresUsersRepositoryModule.USER_CONFIGURATION_MODULE,
            new UsersRepositoryModuleChooser(
                DatabaseCombinedUserRequireModule.of(PostgresUsersDAO.class),
                new PostgresUsersRepositoryModule())
                .chooseModule(configuration.usersRepositoryImplementation()));
    }

    private static Module chooseRedisRateLimiterModule(PostgresTmailConfiguration configuration) {
        if (configuration.hasConfigurationProperties("redis")) {
            return new RedisRateLimiterModule();
        }
        return Modules.EMPTY_MODULE;
    }

    private static Module chooseRspamdModule(PostgresTmailConfiguration configuration) {
        if (configuration.hasConfigurationProperties("rspamd")) {
            return new RspamdModule();
        }
        return Modules.EMPTY_MODULE;
    }

    private static Module chooseJmapModule(PostgresTmailConfiguration configuration) {
        if (configuration.jmapEnabled()) {
            return Modules.combine(chooseJmapEventBusModule(configuration), new JMAPListenerModule());
        }
        return binder -> {
        };
    }

    public static Module chooseJmapEventBusModule(PostgresTmailConfiguration configuration) {
        if (configuration.eventBusImpl().equals(RABBITMQ)) {
            return new JMAPEventBusModule();
        }
        return Modules.EMPTY_MODULE;
    }

    private static List<Module> chooseFirebase(FirebaseModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new PostgresFirebaseRepositoryModule(), new FirebaseCommonModule());
        }
        return List.of();
    }

    public static List<Module> chooseSearchModules(PostgresTmailConfiguration configuration) {
        switch (configuration.searchConfiguration().getImplementation()) {
            case OpenSearch:
                return List.of(
                    new OSContactAutoCompleteModule(),
                    new OpenSearchClientModule(),
                    new OpenSearchMailboxModule(),
                    new ReIndexingModule());
            case Scanning:
                return List.of(
                    binder -> binder.bind(EmailAddressContactSearchEngine.class).toInstance(new InMemoryEmailAddressContactSearchEngine()),
                    SCANNING_SEARCH_MODULE,
                    SCANNING_QUOTA_SEARCH_MODULE);
            case OpenSearchDisabled:
                return List.of(
                    binder -> binder.bind(EmailAddressContactSearchEngine.class).to(DisabledEmailAddressContactSearchEngine.class),
                    new OpenSearchDisabledModule(),
                    SCANNING_QUOTA_SEARCH_MODULE);
            default:
                throw new RuntimeException("Unsupported search implementation " + configuration.searchConfiguration().getImplementation());
        }
    }

    private static List<Module> chooseLinagoraServicesDiscovery(LinagoraServicesDiscoveryModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new LinagoraServicesDiscoveryModule());
        }
        return List.of();
    }

    private static Module chooseMailbox(MailboxConfiguration mailboxConfiguration) {
        if (mailboxConfiguration.isEncryptionEnabled()) {
            return new PostgresEncryptedMailboxModule();
        }
        return Modules.EMPTY_MODULE;
    }

    private static Module chooseRLSSupportPostgresMailboxModule(PostgresTmailConfiguration configuration) {
        if (configuration.rlsEnabled()) {
            return new RLSSupportPostgresMailboxModule();
        }
        return Modules.EMPTY_MODULE;
    }
}
