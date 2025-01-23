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

import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLED;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CARDDAV;
import static com.linagora.tmail.OpenPaasModuleChooserConfiguration.ENABLE_CONTACTS_CONSUMER;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.OpenPaasTestModule;
import com.linagora.tmail.UsersRepositoryModuleChooser;
import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.carddav.CardDavServerExtension;
import com.linagora.tmail.combined.identity.LdapExtension;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class DistributedServerWithOpenPaasConfiguredTest {

    @RegisterExtension
    static OpenPaasServerExtension openPaasServerExtension = new OpenPaasServerExtension();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

    @Nested
    class ContactsConsumer {
        static Function<RabbitMQExtension, OpenPaasConfiguration.ContactConsumerConfiguration> contactConsumerConfigurationFunction = rabbitMQExtension -> new OpenPaasConfiguration.ContactConsumerConfiguration(
            ImmutableList.of(AmqpUri.from(Throwing.supplier(() -> rabbitMQExtension.dockerRabbitMQ().amqpUri()).get())),
            OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED);

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
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
                .overrideWith(new OpenPaasTestModule(openPaasServerExtension, Optional.empty(),
                    Optional.of(contactConsumerConfigurationFunction.apply(rabbitMQExtension)))))
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
        static CardDavServerExtension cardDavServerExtension = new CardDavServerExtension();

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
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
                .overrideWith(new OpenPaasTestModule(openPaasServerExtension, Optional.of(cardDavServerExtension.getCardDavConfiguration()), Optional.empty())))
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
