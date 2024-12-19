package com.linagora.tmail.james.app;

import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLED;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CARDDAV;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CONTACTS_CONSUMER;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

class MemoryServerWithOpenPaasConfiguredTest {

    @Nested
    class ContactsConsumer {
        @RegisterExtension
        static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .mailbox(new MailboxConfiguration(false))
                .usersRepository(DEFAULT)
                .openPaasModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED, !ENABLE_CARDDAV, ENABLE_CONTACTS_CONSUMER))
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(new RabbitMQModule()))
            .extension(new RabbitMQExtension())
            .build();

        @Test
        void serverShouldStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isTrue();
        }
    }

    @Nested
    class CardDav {

        @RegisterExtension
        static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .mailbox(new MailboxConfiguration(false))
                .usersRepository(DEFAULT)
                .openPaasModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED, ENABLE_CARDDAV, !ENABLE_CONTACTS_CONSUMER))
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(new RabbitMQModule()))
            .extension(new RabbitMQExtension())
            .build();

        @Test
        void serverShouldStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isTrue();
        }
    }
}