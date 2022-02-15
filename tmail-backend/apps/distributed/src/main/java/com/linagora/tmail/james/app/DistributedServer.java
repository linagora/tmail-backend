package com.linagora.tmail.james.app;

import static org.apache.james.JamesServerMain.LOGGER;

import java.util.List;
import java.util.Set;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.SearchModuleChooser;
import org.apache.james.eventsourcing.eventstore.cassandra.EventNestedTypes;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.cassandra.CassandraMailboxManager;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.CassandraConsistencyTaskSerializationModule;
import org.apache.james.modules.DistributedTaskManagerModule;
import org.apache.james.modules.DistributedTaskSerializationModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.data.CassandraDLPConfigurationStoreModule;
import org.apache.james.modules.data.CassandraDomainListModule;
import org.apache.james.modules.data.CassandraJmapModule;
import org.apache.james.modules.data.CassandraRecipientRewriteTableModule;
import org.apache.james.modules.data.CassandraSieveRepositoryModule;
import org.apache.james.modules.data.CassandraVacationModule;
import org.apache.james.modules.event.JMAPEventBusModule;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.eventstore.CassandraEventStoreModule;
import org.apache.james.modules.mailbox.CassandraBlobStoreDependenciesModule;
import org.apache.james.modules.mailbox.CassandraDeletedMessageVaultModule;
import org.apache.james.modules.mailbox.CassandraMailboxModule;
import org.apache.james.modules.mailbox.CassandraQuotaMailingModule;
import org.apache.james.modules.mailbox.CassandraSessionModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.mailrepository.CassandraMailRepositoryModule;
import org.apache.james.modules.metrics.CassandraMetricsModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.JmapEventBusModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DLPRoutesModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.InconsistencyQuotasSolvingRoutesModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.JmapTasksModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MailboxesExportRoutesModule;
import org.apache.james.modules.server.MessagesRoutesModule;
import org.apache.james.modules.server.RabbitMailQueueRoutesModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.WebAdminMailOverWebModule;
import org.apache.james.modules.server.WebAdminReIndexingTaskSerializationModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.spamassassin.SpamAssassinListenerModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.modules.webadmin.CassandraRoutesModule;
import org.apache.james.modules.webadmin.InconsistencySolvingRoutesModule;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.linagora.tmail.blob.blobid.list.BlobStoreCacheModulesChooser;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.blob.blobid.list.BlobStoreModulesChooser;
import com.linagora.tmail.combined.identity.UsersRepositoryModuleChooser;
import com.linagora.tmail.encrypted.ClearEmailContentFactory;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.cassandra.CassandraEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.cassandra.EncryptedEmailContentStoreCassandraModule;
import com.linagora.tmail.encrypted.cassandra.KeystoreCassandraModule;
import com.linagora.tmail.james.jmap.ShortLivedTokenModule;
import com.linagora.tmail.james.jmap.jwt.ShortLivedTokenRoutesModule;
import com.linagora.tmail.james.jmap.longlivedtoken.LongLivedTokenStoreCassandraModule;
import com.linagora.tmail.james.jmap.method.CustomMethodModule;
import com.linagora.tmail.james.jmap.method.EmailSendMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterGetMethodModule;
import com.linagora.tmail.james.jmap.method.FilterSetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.james.jmap.method.LongLivedTokenGetMethodModule;
import com.linagora.tmail.james.jmap.method.LongLivedTokenSetMethodModule;
import com.linagora.tmail.james.jmap.oidc.WebFingerModule;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxJmapModule;
import com.linagora.tmail.james.jmap.ticket.CassandraTicketStoreModule;
import com.linagora.tmail.james.jmap.ticket.TicketRoutesModule;
import com.linagora.tmail.team.TeamMailboxModule;
import com.linagora.tmail.webadmin.TeamMailboxRoutesModule;

public class DistributedServer {
    public static final Module WEBADMIN = Modules.combine(
        new CassandraRoutesModule(),
        new DataRoutesModules(),
        new DeletedMessageVaultRoutesModule(),
        new DLPRoutesModule(),
        new InconsistencyQuotasSolvingRoutesModule(),
        new InconsistencySolvingRoutesModule(),
        new JmapTasksModule(),
        new MailboxesExportRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new TeamMailboxModule(),
        new TeamMailboxRoutesModule(),
        new SieveRoutesModule(),
        new WebAdminServerModule(),
        new WebAdminReIndexingTaskSerializationModule(),
        new MessagesRoutesModule(),
        new WebAdminMailOverWebModule());

    public static final Module JMAP = Modules.override(
        new CassandraJmapModule(),
        new CustomMethodModule(),
        new EncryptedEmailContentStoreCassandraModule(),
        new EncryptedEmailDetailedViewGetMethodModule(),
        new EncryptedEmailFastViewGetMethodModule(),
        new EmailSendMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule(),
        new JMAPServerModule(),
        new JmapEventBusModule(),
        new KeystoreCassandraModule(),
        new KeystoreGetMethodModule(),
        new KeystoreSetMethodModule(),
        new LongLivedTokenGetMethodModule(),
        new LongLivedTokenSetMethodModule(),
        new LongLivedTokenStoreCassandraModule(),
        new ShortLivedTokenModule(),
        new ShortLivedTokenRoutesModule(),
        new TicketRoutesModule(),
        new WebFingerModule())
        .with(new CassandraTicketStoreModule(), new TeamMailboxJmapModule());

    public static final Module PROTOCOLS = Modules.combine(
        new CassandraVacationModule(),
        new IMAPServerModule(),
        JMAP,
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
        CASSANDRA_EVENT_STORE_JSON_SERIALIZATION_DEFAULT_MODULE);

    public static final Module CASSANDRA_MAILBOX_MODULE = Modules.combine(
        new CassandraConsistencyTaskSerializationModule(),
        new CassandraMailboxModule(),
        new CassandraDeletedMessageVaultModule(),
        new MailboxModule(),
        new TikaMailboxModule(),
        new SpamAssassinListenerModule());

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
        .with(new RabbitMQModule(),
            new JMAPEventBusModule(),
            new RabbitMailQueueRoutesModule(),
            new RabbitMQEventBusModule(),
            new DistributedTaskSerializationModule(),
            new TeamMailboxModule(),
            new RedisRateLimiterModule());

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
        BlobStoreConfiguration blobStoreConfiguration = configuration.blobStoreConfiguration();
        SearchConfiguration searchConfiguration = configuration.searchConfiguration();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .combineWith(BlobStoreModulesChooser.chooseModules(blobStoreConfiguration))
            .combineWith(BlobStoreCacheModulesChooser.chooseModules(blobStoreConfiguration))
            .combineWith(SearchModuleChooser.chooseModules(searchConfiguration))
            .combineWith(UsersRepositoryModuleChooser.chooseModules(configuration.getUsersRepositoryImplementation()))
            .overrideWith(chooseMailbox(configuration.mailboxConfiguration()));
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
}
