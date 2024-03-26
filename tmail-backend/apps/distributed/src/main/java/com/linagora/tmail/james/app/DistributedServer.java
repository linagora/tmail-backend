package com.linagora.tmail.james.app;

import static org.apache.james.JamesServerMain.LOGGER;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.events.Group;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.eventsourcing.eventstore.EventNestedTypes;
import org.apache.james.jmap.InjectionKeys;
import org.apache.james.jmap.draft.JMAPListenerModule;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.mailbox.store.search.MessageSearchIndex;
import org.apache.james.mailbox.store.search.SimpleMessageSearchIndex;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.CassandraConsistencyTaskSerializationModule;
import org.apache.james.modules.DistributedTaskManagerModule;
import org.apache.james.modules.DistributedTaskSerializationModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.data.CassandraDLPConfigurationStoreModule;
import org.apache.james.modules.data.CassandraDelegationStoreModule;
import org.apache.james.modules.data.CassandraDomainListModule;
import org.apache.james.modules.data.CassandraJmapModule;
import org.apache.james.modules.data.CassandraRecipientRewriteTableModule;
import org.apache.james.modules.data.CassandraSieveQuotaLegacyModule;
import org.apache.james.modules.data.CassandraSieveQuotaModule;
import org.apache.james.modules.data.CassandraSieveRepositoryModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.data.CassandraVacationModule;
import org.apache.james.modules.event.JMAPEventBusModule;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.eventstore.CassandraEventStoreModule;
import org.apache.james.modules.mailbox.CassandraBlobStoreDependenciesModule;
import org.apache.james.modules.mailbox.CassandraDeletedMessageVaultModule;
import org.apache.james.modules.mailbox.CassandraMailboxModule;
import org.apache.james.modules.mailbox.CassandraMailboxQuotaLegacyModule;
import org.apache.james.modules.mailbox.CassandraMailboxQuotaModule;
import org.apache.james.modules.mailbox.CassandraQuotaMailingModule;
import org.apache.james.modules.mailbox.CassandraSessionModule;
import org.apache.james.modules.mailbox.OpenSearchClientModule;
import org.apache.james.modules.mailbox.OpenSearchDisabledModule;
import org.apache.james.modules.mailbox.OpenSearchMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.mailrepository.CassandraMailRepositoryModule;
import org.apache.james.modules.metrics.CassandraMetricsModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.JmapEventBusModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.rabbitmq.MailQueueViewChoice;
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
import org.apache.james.modules.server.UserIdentityModule;
import org.apache.james.modules.server.WebAdminMailOverWebModule;
import org.apache.james.modules.server.WebAdminReIndexingTaskSerializationModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.modules.webadmin.CassandraRoutesModule;
import org.apache.james.modules.webadmin.InconsistencySolvingRoutesModule;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.quota.search.scanning.ScanningQuotaSearcher;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.apache.james.user.cassandra.CassandraUsersDAO;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.linagora.tmail.DatabaseCombinedUserRequireModule;
import com.linagora.tmail.ScheduledReconnectionHandler;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.blob.blobid.list.BlobStoreCacheModulesChooser;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.blob.blobid.list.BlobStoreModulesChooser;
import com.linagora.tmail.contact.RabbitMQEmailAddressContactModule;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.cassandra.CassandraEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.cassandra.EncryptedEmailContentStoreCassandraModule;
import com.linagora.tmail.encrypted.cassandra.KeystoreCassandraModule;
import com.linagora.tmail.event.DistributedEmailAddressContactEventModule;
import com.linagora.tmail.event.RabbitMQAndRedisEventBusModule;
import com.linagora.tmail.healthcheck.TasksHeathCheckModule;
import com.linagora.tmail.james.jmap.TMailJMAPModule;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.firebase.CassandraFirebaseSubscriptionRepositoryModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseCommonModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushListener;
import com.linagora.tmail.james.jmap.firebase.FirebasePushListenerRegister;
import com.linagora.tmail.james.jmap.label.CassandraLabelRepositoryModule;
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
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.settings.CassandraJmapSettingsRepositoryModule;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxJmapModule;
import com.linagora.tmail.james.jmap.ticket.CassandraTicketStoreModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;
import com.linagora.tmail.rate.limiter.api.cassandra.module.CassandraRateLimitingModule;
import com.linagora.tmail.rspamd.RspamdModule;
import com.linagora.tmail.team.TeamMailboxModule;
import com.linagora.tmail.webadmin.EmailAddressContactRoutesModule;
import com.linagora.tmail.webadmin.RateLimitPlanRoutesModule;
import com.linagora.tmail.webadmin.TeamMailboxRoutesModule;
import com.linagora.tmail.webadmin.archival.InboxArchivalTaskModule;
import com.linagora.tmail.webadmin.cleanup.MailboxesCleanupModule;

import jakarta.mail.Flags;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DistributedServer {
    private static class ScanningQuotaSearchModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(ScanningQuotaSearcher.class).in(Scopes.SINGLETON);
            bind(QuotaSearcher.class).to(ScanningQuotaSearcher.class);
        }
    }

    // Required for CLI
    private static class FakeMessageSearchIndex extends ListeningMessageSearchIndex {

        public FakeMessageSearchIndex() {
            super(null, ImmutableSet.of(), null);
        }

        @Override
        public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Group getDefaultGroup() {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, long limit) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public EnumSet<MailboxManager.SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
            throw new NotImplementedException("not implemented");
        }

        @Override
        public ExecutionMode getExecutionMode() {
            throw new NotImplementedException("not implemented");
        }
    }

    private static class ScanningSearchModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(MessageSearchIndex.class).to(SimpleMessageSearchIndex.class);
            bind(FakeMessageSearchIndex.class).in(Scopes.SINGLETON);
            bind(ListeningMessageSearchIndex.class).to(FakeMessageSearchIndex.class);
        }
    }

    public static final Module WEBADMIN = Modules.combine(
        new CassandraRoutesModule(),
        new DataRoutesModules(),
        new DeletedMessageVaultRoutesModule(),
        new DLPRoutesModule(),
        new InconsistencyQuotasSolvingRoutesModule(),
        new InconsistencySolvingRoutesModule(),
        new JmapTasksModule(),
        new JmapUploadCleanupModule(),
        new MailboxesExportRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new RateLimitPlanRoutesModule(),
        new TeamMailboxModule(),
        new TeamMailboxRoutesModule(),
        new SieveRoutesModule(),
        new WebAdminServerModule(),
        new WebAdminReIndexingTaskSerializationModule(),
        new MessagesRoutesModule(),
        new WebAdminMailOverWebModule(),
        new EmailAddressContactRoutesModule(),
        new UserIdentityModule(),
        new MailboxesCleanupModule(),
        new InboxArchivalTaskModule());

    public static final Module JMAP = Modules.override(
        new TMailJMAPModule(),
        new CalendarEventMethodModule(),
        new ContactAutocompleteMethodModule(),
        new CassandraJmapModule(),
        new CustomMethodModule(),
        new EncryptedEmailContentStoreCassandraModule(),
        new EncryptedEmailDetailedViewGetMethodModule(),
        new EncryptedEmailFastViewGetMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new ForwardGetMethodModule(),
        new ForwardSetMethodModule(),
        new JMAPServerModule(),
        new JmapEventBusModule(),
        new KeystoreCassandraModule(),
        new KeystoreGetMethodModule(),
        new KeystoreSetMethodModule(),
        new TicketRoutesModule(),
        new WebFingerModule(),
        new EmailRecoveryActionMethodModule(),
        new LabelMethodModule(),
        new JmapSettingsMethodModule())
        .with(new CassandraTicketStoreModule(), new TeamMailboxJmapModule());

    public static final Module PROTOCOLS = Modules.combine(
        new CassandraVacationModule(),
        new IMAPServerModule(),
        JMAP,
        new ManageSieveServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        WEBADMIN);

    private static final Module BLOB_MODULE = Modules.combine(
        new BlobExportMechanismModule());

    private static final Module CASSANDRA_EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE = binder ->
        binder.bind(new TypeLiteral<Set<DTOModule<?, ? extends DTO>>>() {}).annotatedWith(Names.named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME))
            .toInstance(ImmutableSet.of());

    public static final Module CASSANDRA_SERVER_CORE_MODULE = Modules.combine(
        new CassandraBlobStoreDependenciesModule(),
        new CassandraDomainListModule(),
        new CassandraDLPConfigurationStoreModule(),
        new CassandraEventStoreModule(),
        new CassandraMailRepositoryModule(),
        new CassandraMetricsModule(),
        new CassandraRecipientRewriteTableModule(),
        new CassandraSessionModule(),
        new CassandraSieveRepositoryModule(),
        BLOB_MODULE,
        CASSANDRA_EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE,
        new CassandraDelegationStoreModule());

    public static final Module CASSANDRA_MAILBOX_MODULE = Modules.combine(
        new CassandraConsistencyTaskSerializationModule(),
        new CassandraMailboxModule(),
        new CassandraDeletedMessageVaultModule(),
        new MailboxModule(),
        new TikaMailboxModule());

    public static final Module PLUGINS = Modules.combine(
        new CassandraQuotaMailingModule());

    public static Module REQUIRE_TASK_MANAGER_MODULE = Modules.combine(
        CASSANDRA_SERVER_CORE_MODULE,
        CASSANDRA_MAILBOX_MODULE,
        PROTOCOLS,
        PLUGINS,
        new DKIMMailetModule());

    public static final Module MODULES = Modules
        .override(Modules.combine(
            new MailetProcessingModule(),
            REQUIRE_TASK_MANAGER_MODULE,
            new DistributedTaskManagerModule()))
        .with(new CassandraLabelRepositoryModule(),
            new CassandraRateLimitingModule(),
            new CassandraJmapSettingsRepositoryModule(),
            new DistributedEmailAddressContactEventModule(),
            new DistributedTaskSerializationModule(),
            new JMAPEventBusModule(),
            new RabbitMQEmailAddressContactModule(),
            new RabbitMQEventBusModule(),
            new RabbitMQModule(),
            new RabbitMQMailQueueModule(),
            new RabbitMailQueueRoutesModule(),
            new ScheduledReconnectionHandler.Module(),
            new TasksHeathCheckModule(),
            new TeamMailboxModule(),
            new TMailMailboxSortOrderProviderModule());

    public static void main(String[] args) throws Exception {
        DistributedJamesConfiguration configuration = DistributedJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(DistributedJamesConfiguration configuration) {
        ExtraProperties.initialize();

        BlobStoreConfiguration blobStoreConfiguration = configuration.blobStoreConfiguration();
        SearchConfiguration searchConfiguration = configuration.searchConfiguration();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .combineWith(MailQueueViewChoice.ModuleChooser.choose(configuration.mailQueueViewChoice()))
            .combineWith(BlobStoreModulesChooser.chooseModules(blobStoreConfiguration,
                BlobStoreModulesChooser.SingleSaveDeclarationModule.BackedStorage.CASSANDRA))
            .combineWith(BlobStoreCacheModulesChooser.chooseModules(blobStoreConfiguration))
            .combineWith(chooseUsersModule(configuration))
            .combineWith(chooseFirebase(configuration.firebaseModuleChooserConfiguration()))
            .combineWith(chooseLinagoraServicesDiscovery(configuration.linagoraServicesDiscoveryModuleChooserConfiguration()))
            .combineWith(chooseRedisRateLimiterModule(configuration))
            .combineWith(chooseRspamdModule(configuration))
            .combineWith(chooseQuotaModule(configuration))
            .overrideWith(chooseModules(searchConfiguration))
            .overrideWith(chooseMailbox(configuration.mailboxConfiguration()))
            .overrideWith(chooseJmapModule(configuration))
            .overrideWith(overrideEventBusModule(configuration));
    }

    private static Module chooseUsersModule(DistributedJamesConfiguration configuration) {
        return new UsersRepositoryModuleChooser(
            DatabaseCombinedUserRequireModule.of(CassandraUsersDAO.class),
            new CassandraUsersRepositoryModule())
            .chooseModule(configuration.usersRepositoryImplementation());
    }

    public static List<Module> chooseModules(SearchConfiguration searchConfiguration) {
        switch (searchConfiguration.getImplementation()) {
            case OpenSearch:
                return ImmutableList.of(
                    new OSContactAutoCompleteModule(),
                    new OpenSearchClientModule(),
                    new OpenSearchMailboxModule(),
                    new ReIndexingModule());
            case Scanning:
                return ImmutableList.of(
                    binder -> binder.bind(EmailAddressContactSearchEngine.class).to(DisabledEmailAddressContactSearchEngine.class),
                    new ScanningQuotaSearchModule(),
                    new ScanningSearchModule());
            case OpenSearchDisabled:
                return ImmutableList.of(
                    binder -> binder.bind(EmailAddressContactSearchEngine.class).to(DisabledEmailAddressContactSearchEngine.class),
                    new OpenSearchDisabledModule(),
                    new ScanningQuotaSearchModule());
            default:
                throw new RuntimeException("Unsupported search implementation " + searchConfiguration.getImplementation());
        }
    }

    private static Module chooseJmapModule(DistributedJamesConfiguration configuration) {
        if (configuration.jmapEnabled()) {
            return new JMAPListenerModule();
        }
        return binder -> {
        };
    }

    private static class EncryptedMailboxModule extends AbstractModule {
        @Provides
        @Singleton
        MailboxManager provide(CassandraMailboxManager mailboxManager, KeystoreManager keystoreManager,
                               ClearEmailContentFactory clearEmailContentFactory,
                               CassandraEncryptedEmailContentStore contentStore) {
            return new EncryptedMailboxManager(mailboxManager, keystoreManager, clearEmailContentFactory, contentStore);
        }
    }

    private static List<Module> chooseMailbox(MailboxConfiguration mailboxConfiguration) {
        if (mailboxConfiguration.isEncryptionEnabled()) {
            return ImmutableList.of(new EncryptedMailboxModule());
        }
        return ImmutableList.of();
    }

    private static class FirebaseListenerDistributedModule extends AbstractModule {
        @ProvidesIntoSet
        InitializationOperation registerFirebaseListener(@Named(InjectionKeys.JMAP) RabbitMQEventBus instance, FirebasePushListener firebasePushListener) {
            return InitilizationOperationBuilder
                .forClass(FirebasePushListenerRegister.class)
                .init(() -> instance.register(firebasePushListener));
        }
    }

    private static List<Module> chooseFirebase(FirebaseModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new CassandraFirebaseSubscriptionRepositoryModule(), new FirebaseCommonModule(), new FirebaseListenerDistributedModule());
        }
        return List.of();
    }

    private static List<Module> chooseLinagoraServicesDiscovery(LinagoraServicesDiscoveryModuleChooserConfiguration moduleChooserConfiguration) {
        if (moduleChooserConfiguration.enable()) {
            return List.of(new LinagoraServicesDiscoveryModule());
        }
        return List.of();
    }

    private static List<Module> chooseRedisRateLimiterModule(DistributedJamesConfiguration configuration) {
        try {
            configuration.propertiesProvider().getConfiguration("redis");
            return List.of(new RedisRateLimiterModule());
        } catch (FileNotFoundException notFoundException) {
            LOGGER.info("Redis configuration not found, disabling Redis rate limiter module");
            return List.of();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<Module> chooseRspamdModule(DistributedJamesConfiguration configuration) {
        try {
            configuration.propertiesProvider().getConfiguration("rspamd");
            return List.of(new RspamdModule());
        } catch (FileNotFoundException notFoundException) {
            LOGGER.info("Rspamd configuration not found, disabling Rspamd module");
            return List.of();
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Module chooseQuotaModule(DistributedJamesConfiguration configuration) {
        if (configuration.quotaCompatibilityMode()) {
            return Modules.combine(new CassandraMailboxQuotaLegacyModule(), new CassandraSieveQuotaLegacyModule());
        } else {
            return Modules.combine(new CassandraMailboxQuotaModule(), new CassandraSieveQuotaModule());
        }
    }

    private static Module overrideEventBusModule(DistributedJamesConfiguration configuration) {
        switch (configuration.eventBusKeysChoice()) {
            case REDIS -> {
                LOGGER.info("Using Redis for Event Bus user notifications");
                return new RabbitMQAndRedisEventBusModule();
            }
            case RABBITMQ -> {
                LOGGER.info("Using RabbitMQ for Event Bus user notifications");
                return Modules.EMPTY_MODULE;
            }
            default -> throw new NotImplementedException();
        }
    }
}
