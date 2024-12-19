package com.linagora.tmail.james.app;

import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLED;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CARDDAV;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CONTACTS_CONSUMER;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.combined.identity.LdapExtension;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;

public class DistributedServerWithOpenPaasConfiguredTest {

    @Nested
    class ContactsConsumer {
        @RegisterExtension
        static JamesServerExtension
            testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
            DistributedJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .searchConfiguration(SearchConfiguration.openSearch())
                .usersRepository(UsersRepositoryModuleChooser.Implementation.COMBINED)
                .eventBusKeysChoice(EventBusKeysChoice.REDIS)
                .openPassModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED, !ENABLE_CARDDAV, ENABLE_CONTACTS_CONSUMER))
                .build())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new LdapExtension())
            .extension(new RedisExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();

        @Test
        void serverShouldStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isTrue();
        }
    }

    @Nested
    class CardDav {
        @RegisterExtension
        static JamesServerExtension
            testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
            DistributedJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .searchConfiguration(SearchConfiguration.openSearch())
                .usersRepository(UsersRepositoryModuleChooser.Implementation.COMBINED)
                .eventBusKeysChoice(EventBusKeysChoice.REDIS)
                .openPassModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED, ENABLE_CARDDAV, !ENABLE_CONTACTS_CONSUMER))
                .build())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .extension(new LdapExtension())
            .extension(new RedisExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();

        @Test
        void serverShouldStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isTrue();
        }
    }

}
