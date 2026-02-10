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

package com.linagora.tmail.james.app;

import static com.linagora.tmail.OpenPaasModule.DavModule.CALDAV_SUPPORTED;
import static com.linagora.tmail.OpenPaasModule.DavModule.CALDAV_SUPPORT_MODULE_PROVIDER;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.OpenSearchHighlightModule;
import org.apache.james.PostgresJmapModule;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisHealthCheck;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.eventsourcing.eventstore.EventNestedTypes;
import org.apache.james.jmap.JMAPListenerModule;
import org.apache.james.jmap.JMAPModule;
import org.apache.james.jmap.rfc8621.RFC8621MethodsModule;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.quota.QuotaRootDeserializer;
import org.apache.james.mailbox.searchhighligt.SearchHighlighter;
import org.apache.james.mailbox.searchhighligt.SearchSnippet;
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy;
import org.apache.james.mailbox.store.mail.model.impl.MessageParser;
import org.apache.james.mailbox.store.quota.DefaultQuotaChangeNotifier;
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver;
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
import org.apache.james.modules.data.PostgresDataJmapModule;
import org.apache.james.modules.data.PostgresDelegationStoreModule;
import org.apache.james.modules.data.PostgresEventStoreModule;
import org.apache.james.modules.data.PostgresVacationModule;
import org.apache.james.modules.data.SievePostgresRepositoryModules;
import org.apache.james.modules.event.ContentDeletionEventBusModule;
import org.apache.james.modules.event.JMAPEventBusModule;
import org.apache.james.modules.event.MailboxEventBusModule;
import org.apache.james.modules.events.PostgresDeadLetterModule;
import org.apache.james.modules.mailbox.DefaultEventModule;
import org.apache.james.modules.mailbox.OpenSearchClientModule;
import org.apache.james.modules.mailbox.OpenSearchDisabledModule;
import org.apache.james.modules.mailbox.OpenSearchMailboxModule;
import org.apache.james.modules.mailbox.PostgresMailboxModule;
import org.apache.james.modules.mailbox.PostgresMemoryContentDeletionEventBusModule;
import org.apache.james.modules.mailbox.RLSSupportPostgresMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.plugins.QuotaMailingModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.JmapEventBusModule;
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
import org.apache.james.utils.GuiceLoader;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.linagora.tmail.DatabaseCombinedUserRequireModule;
import com.linagora.tmail.ExtensionModuleProvider;
import com.linagora.tmail.NoopGuiceLoader;
import com.linagora.tmail.OpenPaasContactsConsumerModule;
import com.linagora.tmail.OpenPaasModule;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.ScheduledReconnectionHandler;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.blob.guice.BlobStoreModulesChooser;
import com.linagora.tmail.disconnector.EventBusDisconnectorModule;
import com.linagora.tmail.event.RabbitMQAndRedisEventBusModule;
import com.linagora.tmail.event.TMailJMAPListenerModule;
import com.linagora.tmail.event.TmailEventModule;
import com.linagora.tmail.healthcheck.TasksHeathCheckModule;
import com.linagora.tmail.imap.TMailIMAPModule;
import com.linagora.tmail.james.app.modules.jmap.MemoryTmailEventBusModule;
import com.linagora.tmail.james.jmap.TMailJMAPModule;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.james.jmap.contact.RabbitMQEmailAddressContactModule;
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
import com.linagora.tmail.james.jmap.method.FilterGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterSetMethodModule;
import com.linagora.tmail.james.jmap.method.FolderFilteringActionMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardGetMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardSetMethodModule;
import com.linagora.tmail.james.jmap.method.JmapSettingsMethodModule;
import com.linagora.tmail.james.jmap.method.LabelMethodModule;
import com.linagora.tmail.james.jmap.method.MailboxClearMethodModule;
import com.linagora.tmail.james.jmap.module.OSContactAutoCompleteModule;
import com.linagora.tmail.james.jmap.oidc.JMAPOidcModule;
import com.linagora.tmail.james.jmap.oidc.OidcTokenCacheModuleChooser;
import com.linagora.tmail.james.jmap.oidc.WebFingerModule;
import com.linagora.tmail.james.jmap.perfs.TMailCleverAttachmentIdAssignationStrategy;
import com.linagora.tmail.james.jmap.perfs.TMailCleverBlobResolverModule;
import com.linagora.tmail.james.jmap.perfs.TMailCleverMessageParser;
import com.linagora.tmail.james.jmap.publicAsset.PostgresPublicAssetRepositoryModule;
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetsModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModule;
import com.linagora.tmail.james.jmap.service.discovery.LinagoraServicesDiscoveryModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsRepositoryModule;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModule;
import com.linagora.tmail.james.jmap.settings.TWPSettingsModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxJmapModule;
import com.linagora.tmail.james.jmap.ticket.PostgresTicketStoreModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;
import com.linagora.tmail.listener.CollectTrustedContactsListenerModule;
import com.linagora.tmail.mailbox.opensearch.TmailOpenSearchMailboxMappingModule;
import com.linagora.tmail.mailbox.quota.postgres.PostgresUserQuotaReporterModule;
import com.linagora.tmail.modules.data.TMailPostgresDataModule;
import com.linagora.tmail.modules.data.TMailPostgresDeletedMessageVaultModule;
import com.linagora.tmail.modules.data.TMailPostgresUsersRepositoryModule;
import com.linagora.tmail.rate.limiter.api.postgres.module.PostgresRateLimitingModule;
import com.linagora.tmail.rspamd.RspamdModule;
import com.linagora.tmail.team.TMailQuotaUsernameSupplier;
import com.linagora.tmail.team.TeamMailboxModule;
import com.linagora.tmail.webadmin.EmailAddressContactRoutesModule;
import com.linagora.tmail.webadmin.OidcBackchannelLogoutRoutesModule;
import com.linagora.tmail.webadmin.RateLimitsRoutesModule;
import com.linagora.tmail.webadmin.TeamMailboxRoutesModule;
import com.linagora.tmail.webadmin.archival.InboxArchivalTaskModule;
import com.linagora.tmail.webadmin.cleanup.MailboxesCleanupModule;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingModule;
import com.linagora.tmail.webadmin.quota.UserQuotaReporterRoutesModule;

import reactor.core.publisher.Mono;

public class PostgresTmailServer {
    private static class FakeSearchHighlighter implements SearchHighlighter {

        @Override
        public Publisher<SearchSnippet> highlightSearch(List<MessageId> messageIds, MultimailboxesSearchQuery expression, MailboxSession session) {
            return Mono.error(new NotImplementedException("not implemented"));
        }
    }

    private static class FakeSearchHighlightModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(SearchHighlighter.class).toInstance(new FakeSearchHighlighter());
        }
    }

    static final Logger LOGGER = LoggerFactory.getLogger("org.apache.james.CONFIGURATION");

    private static final Module EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE = binder ->
        binder.bind(new TypeLiteral<Set<DTOModule<?, ? extends DTO>>>() {
            }).annotatedWith(Names.named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME))
            .toInstance(Set.of());

    private static final Module QUOTA_USERNAME_SUPPLIER_MODULE = new AbstractModule() {
        @Override
        protected void configure() {
            bind(TMailQuotaUsernameSupplier.class).in(Scopes.SINGLETON);
            bind(DefaultQuotaChangeNotifier.UsernameSupplier.class).to(TMailQuotaUsernameSupplier.class);
        }
    };

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
            .overrideWith(ExtensionModuleProvider.extentionModules(configuration.extentionConfiguration()))
            .overrideWith(binder -> {
                binder.bind(GuiceLoader.class).to(NoopGuiceLoader.class);
                binder.bind(NoopGuiceLoader.class).in(Scopes.SINGLETON);
            })
            .overrideWith(chooseOpenPaasModule(configuration.openPaasModuleChooserConfiguration()))
            .overrideWith(chooseJmapModule(configuration))
            .overrideWith(chooseTaskManagerModules(configuration))
            .overrideWith(chooseJmapOidc(configuration))
            .overrideWith(chooseTWPSettingsModule(configuration.twpSettingsModuleChooserConfiguration()))
            .overrideWith(chooseEventBusModules(configuration.eventBusImpl()));
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
        new RateLimitsRoutesModule(),
        new UserQuotaReporterRoutesModule(),
        new ReIndexingModule(),
        new SieveRoutesModule(),
        new TeamMailboxModule(),
        new TeamMailboxRoutesModule(),
        new UserIdentityModule(),
        new ContactIndexingModule(),
        new WebAdminMailOverWebModule(),
        new WebAdminReIndexingTaskSerializationModule(),
        new WebAdminServerModule());

    public static final Module JMAP_LINAGORA = Modules.override(
        new PostgresJmapModule(),
        new PostgresDataJmapModule(),
        new JmapEventBusModule(),
        new JMAPServerModule(),
        new JMAPModule(),
        new RFC8621MethodsModule(),
        new TMailCleverBlobResolverModule(),
        new TMailJMAPModule(),
        new CalendarEventMethodModule(),
        new ContactAutocompleteMethodModule(),
        new CustomMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new ForwardGetMethodModule(),
        new ForwardSetMethodModule(),
        new TicketRoutesModule(),
        new WebFingerModule(),
        new EmailRecoveryActionMethodModule(),
        new LabelMethodModule(),
        new JmapSettingsMethodModule(),
        new PublicAssetsModule(),
        new MailboxClearMethodModule(),
        new FolderFilteringActionMethodModule())
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
        Modules.override(new PostgresMailboxModule())
            .with(binder -> {
                binder.bind(AttachmentIdAssignationStrategy.class).to(TMailCleverAttachmentIdAssignationStrategy.class);
                binder.bind(MessageParser.class).to(TMailCleverMessageParser.class);
                binder.bind(QuotaRootDeserializer.class).toInstance(new DefaultUserQuotaRootResolver.DefaultQuotaRootDeserializer());
            }),
        new PostgresDeadLetterModule(),
        new TMailPostgresDataModule(),
        new MailboxModule(),
        new SievePostgresRepositoryModules(),
        new TaskManagerModule(),
        new TikaMailboxModule(),
        new PostgresVacationModule(),
        new PostgresDLPConfigurationStoreModule(),
        new TMailPostgresDeletedMessageVaultModule(),
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
            new PostgresUserQuotaReporterModule(),
            new RateLimitsRoutesModule(),
            new EmailAddressContactRoutesModule(),
            new PostgresLabelRepositoryModule(),
            new PostgresJmapSettingsRepositoryModule(),
            new PostgresPublicAssetRepositoryModule(),
            new PostgresTicketStoreModule(),
            new TasksHeathCheckModule(),
            chooseQueueModules(configuration),
            new TMailIMAPModule(),
            QUOTA_USERNAME_SUPPLIER_MODULE,
            new EventBusDisconnectorModule(),
            new CollectTrustedContactsListenerModule());

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
        new MemoryTmailEventBusModule());

    private static final Module RABBITMQ_EVENT_BUS_FEATURE_MODULE = Modules.combine(
        new TmailEventModule(),
        new MailboxEventBusModule(),
        new ContentDeletionEventBusModule(),
        new JMAPEventBusModule());

    public static Module chooseQueueModules(PostgresTmailConfiguration configuration) {
        return switch (configuration.eventBusImpl()) {
            case IN_MEMORY -> Modules.combine(new DefaultEventModule(),
                new ActiveMQQueueModule(),
                new PostgresMemoryContentDeletionEventBusModule());
            case RABBITMQ, RABBITMQ_AND_REDIS -> Modules.combine(new RabbitMQModule(),
                new RabbitMQMailQueueModule(),
                new FakeMailQueueViewModule(),
                new RabbitMailQueueRoutesModule(),
                new RabbitMQEmailAddressContactModule(),
                new ScheduledReconnectionHandler.Module(),
                new DistributedTaskSerializationModule(),
                new DefaultEventModule());
        };
    }

    public static Module chooseEventBusModules(PostgresTmailConfiguration.EventBusImpl eventBusImpl) {
        return switch (eventBusImpl) {
            case IN_MEMORY -> IN_MEMORY_EVENT_BUS_FEATURE_MODULE;
            case RABBITMQ -> RABBITMQ_EVENT_BUS_FEATURE_MODULE;
            case RABBITMQ_AND_REDIS -> new RabbitMQAndRedisEventBusModule();
        };
    }

    public static List<Module> chooseTaskManagerModules(PostgresTmailConfiguration configuration) {
        switch (configuration.eventBusImpl()) {
            case IN_MEMORY:
                return List.of(new TaskManagerModule(), new PostgresTaskExecutionDetailsProjectionGuiceModule());
            case RABBITMQ, RABBITMQ_AND_REDIS:
                return List.of(new DistributedTaskManagerModule());
            default:
                throw new RuntimeException("Unsupported event-bus implementation " + configuration.eventBusImpl().name());
        }
    }

    public static Module chooseUserRepositoryModule(PostgresTmailConfiguration configuration) {
        return Modules.combine(TMailPostgresUsersRepositoryModule.USER_CONFIGURATION_MODULE,
            new UsersRepositoryModuleChooser(
                DatabaseCombinedUserRequireModule.of(PostgresUsersDAO.class),
                new TMailPostgresUsersRepositoryModule())
                .chooseModule(configuration.usersRepositoryImplementation()));
    }

    private static Module chooseRedisRateLimiterModule(PostgresTmailConfiguration configuration) {
        if (configuration.hasConfigurationProperties("redis")) {
            return Modules.combine(new RedisRateLimiterModule(), new AbstractModule() {
                @Override
                protected void configure() {
                    Multibinder.newSetBinder(binder(), HealthCheck.class)
                        .addBinding()
                        .to(RedisHealthCheck.class);
                }
            });
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
            return Modules.combine(chooseJmapListenerModule(configuration));
        }
        return binder -> {
        };
    }

    private static Module chooseJmapListenerModule(PostgresTmailConfiguration configuration) {
        if (configuration.searchConfiguration().getImplementation().equals(SearchConfiguration.Implementation.OpenSearch)) {
            return new TMailJMAPListenerModule();
        }
        return new JMAPListenerModule();
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
                    new TmailOpenSearchMailboxMappingModule(),
                    new OpenSearchMailboxModule(),
                    new ReIndexingModule(),
                    new OpenSearchHighlightModule());
            case Scanning:
                return List.of(
                    new InMemoryEmailAddressContactSearchEngineModule(),
                    SCANNING_SEARCH_MODULE,
                    SCANNING_QUOTA_SEARCH_MODULE,
                    new FakeSearchHighlightModule());
            case OpenSearchDisabled:
                return List.of(
                    new DisabledEmailAddressContactSearchEngineModule(),
                    new OpenSearchDisabledModule(),
                    SCANNING_QUOTA_SEARCH_MODULE,
                    new FakeSearchHighlightModule());
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

    private static Module chooseRLSSupportPostgresMailboxModule(PostgresTmailConfiguration configuration) {
        if (configuration.rlsEnabled()) {
            return new RLSSupportPostgresMailboxModule();
        }
        return Modules.EMPTY_MODULE;
    }

    private static Module chooseJmapOidc(PostgresTmailConfiguration configuration) {
        if (configuration.oidcEnabled()) {
            return Modules.combine(new JMAPOidcModule(), new OidcBackchannelLogoutRoutesModule(),
                OidcTokenCacheModuleChooser.chooseModule(configuration.oidcTokenCacheChoice()));
        }
        return binder -> {

        };
    }

    private static List<Module> chooseOpenPaasModule(OpenPaasModuleChooserConfiguration openPaasModuleChooserConfiguration) {
        if (openPaasModuleChooserConfiguration.enabled()) {
            ImmutableList.Builder<Module> moduleBuilder = ImmutableList.<Module>builder().add(new OpenPaasModule());
            if (openPaasModuleChooserConfiguration.shouldEnableDavServerInteraction()) {
                moduleBuilder.add(new OpenPaasModule.DavModule());
            }
            moduleBuilder.add(CALDAV_SUPPORT_MODULE_PROVIDER.apply(openPaasModuleChooserConfiguration.shouldEnableDavServerInteraction()));
            if (openPaasModuleChooserConfiguration.contactsConsumerEnabled()) {
                moduleBuilder.add(new OpenPaasContactsConsumerModule());
            }
            return moduleBuilder.build();
        }
        return List.of(CALDAV_SUPPORT_MODULE_PROVIDER.apply(!CALDAV_SUPPORTED));
    }

    private static List<Module> chooseTWPSettingsModule(TWPSettingsModuleChooserConfiguration twpSettingsModuleChooserConfiguration) {
        if (twpSettingsModuleChooserConfiguration.enabled()) {
            return List.of(new TWPSettingsModule());
        }
        return List.of();
    }
}
