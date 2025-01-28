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

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLED;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_DAV;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CONTACTS_CONSUMER;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.OpenPaasTestModule;
import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.dav.DavServerExtension;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

class MemoryServerWithOpenPaasConfiguredTest {

    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

    @Nested
    class ContactsConsumerConfigured {
        static Function<RabbitMQExtension, OpenPaasConfiguration.ContactConsumerConfiguration> contactConsumerConfigurationFunction = rabbitMQExtension -> new OpenPaasConfiguration.ContactConsumerConfiguration(
            ImmutableList.of(AmqpUri.from(Throwing.supplier(() -> rabbitMQExtension.dockerRabbitMQ().amqpUri()).get())),
            OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED);

        @RegisterExtension
        static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .mailbox(new MailboxConfiguration(false))
                .usersRepository(DEFAULT)
                .openPaasModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED, !ENABLE_DAV, ENABLE_CONTACTS_CONSUMER))
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(new RabbitMQModule())
                .overrideWith(new OpenPaasTestModule(openPaasServerExtension, Optional.empty(),
                    Optional.of(contactConsumerConfigurationFunction.apply(rabbitMQExtension)))))
            .extension(new RabbitMQExtension())
            .build();

        @Test
        void serverShouldStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isTrue();
        }
    }

    @Nested
    class DavConfigured {
        @RegisterExtension
        static DavServerExtension davServerExtension = new DavServerExtension(
            WireMockExtension.extensionOptions()
                .options(wireMockConfig().dynamicPort()));

        @RegisterExtension
        static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .mailbox(new MailboxConfiguration(false))
                .usersRepository(DEFAULT)
                .openPaasModuleChooserConfiguration(new OpenPaasModuleChooserConfiguration(ENABLED,
                    ENABLE_DAV, !ENABLE_CONTACTS_CONSUMER))
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(new RabbitMQModule())
                .overrideWith(new OpenPaasTestModule(openPaasServerExtension, Optional.of(davServerExtension.getDavConfiguration()), Optional.empty())))
            .extension(new RabbitMQExtension())
            .build();

        @Test
        void serverShouldStart(GuiceJamesServer server) {
            assertThat(server.isStarted()).isTrue();
        }
    }
}