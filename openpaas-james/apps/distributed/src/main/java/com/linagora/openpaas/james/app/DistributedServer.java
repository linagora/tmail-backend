package com.linagora.openpaas.james.app;

import static org.apache.james.CassandraJamesServerMain.CASSANDRA_MAILBOX_MODULE;
import static org.apache.james.CassandraJamesServerMain.CASSANDRA_SERVER_CORE_MODULE;
import static org.apache.james.CassandraJamesServerMain.PLUGINS;
import static org.apache.james.CassandraJamesServerMain.WEBADMIN;
import static org.apache.james.JamesServerMain.LOGGER;

import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.SearchConfiguration;
import org.apache.james.SearchModuleChooser;
import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.modules.DistributedTaskManagerModule;
import org.apache.james.modules.DistributedTaskSerializationModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.blobstore.BlobStoreModulesChooser;
import org.apache.james.modules.data.CassandraJmapModule;
import org.apache.james.modules.data.CassandraUsersRepositoryModule;
import org.apache.james.modules.event.RabbitMQEventBusModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.JMAPServerModule;
import org.apache.james.modules.protocols.JmapEventBusModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.server.RabbitMailQueueRoutesModule;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.openpaas.james.jmap.method.CustomMethodModule;
import com.linagora.openpaas.james.jmap.method.FilterGetMethodModule;

public class DistributedServer {
    public static final Module PROTOCOLS = Modules.combine(
        new CassandraJmapModule(),
        new IMAPServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule(),
        new JMAPServerModule(),
        new JmapEventBusModule(),
        WEBADMIN);

    public static Module REQUIRE_TASK_MANAGER_MODULE = Modules.combine(
        CASSANDRA_SERVER_CORE_MODULE,
        CASSANDRA_MAILBOX_MODULE,
        PROTOCOLS,
        PLUGINS,
        new DKIMMailetModule());

    public static final Module MODULES = Modules.combine(
        Modules
            .override(Modules.combine(REQUIRE_TASK_MANAGER_MODULE, new DistributedTaskManagerModule()))
            .with(new RabbitMQModule(),
                new RabbitMailQueueRoutesModule(),
                new RabbitMQEventBusModule(),
                new DistributedTaskSerializationModule()),
       new CustomMethodModule(), new FilterGetMethodModule());

    public static void main(String[] args) throws Exception {
        CassandraRabbitMQJamesConfiguration configuration = CassandraRabbitMQJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(CassandraRabbitMQJamesConfiguration configuration) {
        BlobStoreConfiguration blobStoreConfiguration = configuration.blobStoreConfiguration();
        SearchConfiguration searchConfiguration = configuration.searchConfiguration();

        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES)
            .combineWith(BlobStoreModulesChooser.chooseModules(blobStoreConfiguration))
            .combineWith(BlobStoreCacheModulesChooser.chooseModules(blobStoreConfiguration))
            .combineWith(SearchModuleChooser.chooseModules(searchConfiguration))
            .combineWith(new UsersRepositoryModuleChooser(new CassandraUsersRepositoryModule())
                .chooseModules(configuration.getUsersRepositoryImplementation()));
    }
}
