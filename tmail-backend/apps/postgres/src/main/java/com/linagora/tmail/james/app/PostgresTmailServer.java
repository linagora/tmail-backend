package com.linagora.tmail.james.app;

import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.SearchModuleChooser;
import org.apache.james.data.LdapUsersRepositoryModule;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.blobstore.BlobStoreModulesChooser;
import org.apache.james.modules.data.PostgresDataModule;
import org.apache.james.modules.data.PostgresDelegationStoreModule;
import org.apache.james.modules.data.PostgresUsersRepositoryModule;
import org.apache.james.modules.data.SievePostgresRepositoryModules;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.events.PostgresDeadLetterModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.mailbox.DefaultEventModule;
import org.apache.james.modules.mailbox.PostgresDeletedMessageVaultModule;
import org.apache.james.modules.mailbox.PostgresMailboxModule;
import org.apache.james.modules.mailbox.TikaMailboxModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.activemq.ActiveMQQueueModule;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.InconsistencyQuotasSolvingRoutesModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.ReIndexingModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminReIndexingTaskSerializationModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.modules.vault.DeletedMessageVaultRoutesModule;
import org.apache.james.rate.limiter.redis.RedisRateLimiterModule;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.util.Modules;
import com.linagora.tmail.combined.identity.CombinedUserDAO;
import com.linagora.tmail.combined.identity.CombinedUsersRepositoryModule;

public class PostgresTmailServer {
    static Logger LOGGER = LoggerFactory.getLogger("org.apache.james.CONFIGURATION");

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
        SearchConfiguration searchConfiguration = configuration.searchConfiguration();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(SearchModuleChooser.chooseModules(searchConfiguration))
            .combineWith(chooseUserRepositoryModule(configuration))
            .combineWith(chooseBlobStoreModules(configuration))
            .combineWith(chooseEventBusModules(configuration))
            .combineWith(chooseDeletedMessageVaultModules(configuration))
            .combineWith(chooseRedisRateLimiterModule(configuration))
            .combineWith(POSTGRES_MODULE_AGGREGATE);
    }


    private static final Module WEBADMIN = Modules.combine(
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new InconsistencyQuotasSolvingRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new ReIndexingModule(),
        new SieveRoutesModule(),
        new WebAdminReIndexingTaskSerializationModule());

    private static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new LMTPServerModule(),
        new ManageSieveServerModule(),
        new POP3ServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        WEBADMIN);

    private static final Module POSTGRES_SERVER_MODULE = Modules.combine(
        new ActiveMQQueueModule(),
        new BlobExportMechanismModule(),
        new PostgresDelegationStoreModule(),
        new DefaultProcessorsConfigurationProviderModule(),
        new PostgresMailboxModule(),
        new PostgresDeadLetterModule(),
        new PostgresDataModule(),
        new MailboxModule(),
        new NoJwtModule(),
        new RawPostDequeueDecoratorModule(),
        new SievePostgresRepositoryModules(),
        new TaskManagerModule(),
        new MemoryEventStoreModule(),
        new TikaMailboxModule());
    private static final Module POSTGRES_MODULE_AGGREGATE = Modules.combine(
        new MailetProcessingModule(), POSTGRES_SERVER_MODULE, PROTOCOLS);

    private static Module chooseBlobStoreModules(PostgresTmailConfiguration configuration) {
        return Modules.combine(Modules.combine(BlobStoreModulesChooser.chooseModules(configuration.blobStoreConfiguration())),
            new BlobStoreCacheModulesChooser.CacheDisabledModule());
    }

    public static Module chooseEventBusModules(PostgresTmailConfiguration configuration) {
        return switch (configuration.eventBusImpl()) {
            case IN_MEMORY -> new DefaultEventModule();
            case RABBITMQ -> Modules.combine(new RabbitMQModule(),
                Modules.override(new DefaultEventModule())
                    .with(new RabbitMQEventBusModule()));
        };
    }

    public static Module chooseUserRepositoryModule(PostgresTmailConfiguration configuration) {
        return Modules.combine(PostgresUsersRepositoryModule.USER_CONFIGURATION_MODULE,
            switch (configuration.usersRepositoryImplementation()) {
                case LDAP -> new LdapUsersRepositoryModule();
                case COMBINED -> Modules.override(new PostgresUsersRepositoryModule())
                    .with(Modules.combine(new CombinedUsersRepositoryModule(),
                        new AbstractModule() {
                            @Provides
                            @Singleton
                            @Named(CombinedUserDAO.DATABASE_INJECT_NAME)
                            public UsersDAO provideDatabaseUserDAO(PostgresUsersDAO postgresUsersDAO) {
                                return postgresUsersDAO;
                            }
                        }));
                case DEFAULT -> new PostgresUsersRepositoryModule();
            });
    }

    private static Module chooseDeletedMessageVaultModules(PostgresTmailConfiguration configuration) {
        if (configuration.deletedMessageVaultConfiguration().isEnabled()) {
            return Modules.combine(new PostgresDeletedMessageVaultModule(), new DeletedMessageVaultRoutesModule());
        }
        return Modules.EMPTY_MODULE;
    }

    private static Module chooseRedisRateLimiterModule(PostgresTmailConfiguration configuration) {
        if (configuration.hasConfigurationProperties("redis")) {
            return new RedisRateLimiterModule();
        }
        return Modules.EMPTY_MODULE;
    }

    // TODO: RspamdModule require MessageIdManager, We should do it later
    /*private static Module chooseRspamdModule(PostgresTmailConfiguration configuration) {
        if (configuration.hasConfigurationProperties("rspamd")) {
            return new RspamdModule();
        }
        return Modules.EMPTY_MODULE;
    }*/
}
