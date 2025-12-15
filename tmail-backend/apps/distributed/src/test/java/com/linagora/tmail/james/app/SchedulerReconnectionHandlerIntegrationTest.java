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

import static com.linagora.tmail.ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration.ENABLED;
import static com.linagora.tmail.configuration.OpenPaasConfiguration.OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.vault.VaultConfiguration;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.AmqpUri;
import com.linagora.tmail.OpenPaasContactsConsumerModule;
import com.linagora.tmail.OpenPaasModule;
import com.linagora.tmail.OpenPaasTestModule;
import com.linagora.tmail.ScheduledReconnectionHandler;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;

class SchedulerReconnectionHandlerIntegrationTest {
    private static final Duration FAST_RECONNECTION_HANDLER_INTERVAL = Duration.ofSeconds(10);
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static WireMockOpenPaaSServerExtension openPaasServerExtension = new WireMockOpenPaaSServerExtension();

    static Function<RabbitMQExtension, OpenPaasConfiguration.ContactConsumerConfiguration> contactConsumerConfigurationFunction = rabbitMQExtension -> new OpenPaasConfiguration.ContactConsumerConfiguration(
        ImmutableList.of(AmqpUri.from(Throwing.supplier(() -> rabbitMQExtension.dockerRabbitMQ().amqpUri()).get())),
        OPENPAAS_QUEUES_QUORUM_BYPASS_DISABLED);

    abstract class ContractTests {

        abstract DockerRabbitMQ rabbitMQ();

        @BeforeEach
        void setUp(GuiceJamesServer jamesServer) {
            // ensure all the consumers for vital queues started well
            CALMLY_AWAIT.atMost(20, SECONDS)
                .untilAsserted(() -> SoftAssertions.assertSoftly(softly -> jamesServer.getProbe(ScheduledReconnectionHandlerProbe.class)
                    .getQueuesToMonitor()
                    .forEach(queueName -> softly.assertThat(consumerCount(rabbitMQ(), queueName))
                            .isGreaterThanOrEqualTo(1L))));
        }
    }

    @Nested
    class RabbitMQEventBus extends ContractTests {
        @RegisterExtension
        static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

        @RegisterExtension
        static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
            DistributedJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .noSecondaryS3BlobStore()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .enableSingleSave())
                .searchConfiguration(SearchConfiguration.openSearch())
                .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
                .vaultConfiguration(VaultConfiguration.ENABLED_DEFAULT)
                .build())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(getOpenPaasModule(openPaasServerExtension, rabbitMQExtension))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(binder -> binder.bind(ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration.class)
                    .toInstance(new ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration(ENABLED, FAST_RECONNECTION_HANDLER_INTERVAL)))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(ScheduledReconnectionHandlerProbe.class))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(rabbitMQExtension)
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();

        @Override
        DockerRabbitMQ rabbitMQ() {
            return rabbitMQExtension.dockerRabbitMQ();
        }

        @Test
        void shouldMonitorJamesEventBusGroupQueues(GuiceJamesServer jamesServer) {
            assertThat(jamesServer.getProbe(ScheduledReconnectionHandlerProbe.class)
                .getQueuesToMonitor())
                .contains("mailboxEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup",
                    "jmapEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup",
                    "contentDeletionEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup");
        }
    }

    @Nested
    class RedisEventBus extends ContractTests {
        @RegisterExtension
        static RabbitMQExtension rabbitMQExtension = new RabbitMQExtension();

        @RegisterExtension
        static JamesServerExtension testExtension =  new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
            DistributedJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .noSecondaryS3BlobStore()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .enableSingleSave())
                .searchConfiguration(SearchConfiguration.openSearch())
                .eventBusKeysChoice(EventBusKeysChoice.REDIS)
                .vaultConfiguration(VaultConfiguration.ENABLED_DEFAULT)
                .build())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(getOpenPaasModule(openPaasServerExtension, rabbitMQExtension))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(binder -> binder.bind(ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration.class)
                    .toInstance(new ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration(ENABLED, FAST_RECONNECTION_HANDLER_INTERVAL)))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(ScheduledReconnectionHandlerProbe.class))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(rabbitMQExtension)
            .extension(new RedisExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();

        @Override
        DockerRabbitMQ rabbitMQ() {
            return rabbitMQExtension.dockerRabbitMQ();
        }

        @Test
        void shouldMonitorTMailEventBusGroupQueues(GuiceJamesServer jamesServer) {
            assertThat(jamesServer.getProbe(ScheduledReconnectionHandlerProbe.class)
                .getQueuesToMonitor())
                .contains("mailboxEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                    "jmapEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
                    "contentDeletionEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
        }
    }

    interface RabbitMQManagementClient {
        @RequestLine("GET /api/consumers/%2F")
        @Headers("Authorization: Basic {auth}")
        List<Map<String, Object>> getConsumers(@feign.Param("auth") String auth);
    }

    private long consumerCount(DockerRabbitMQ dockerRabbitMQ, String queueName) {
        try {
            RabbitMQManagementClient client = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .target(RabbitMQManagementClient.class, dockerRabbitMQ.managementUri().toString());
            String auth = java.util.Base64.getEncoder().encodeToString((dockerRabbitMQ.getUsername() + ":" + dockerRabbitMQ.getPassword()).getBytes());
            List<Map<String, Object>> consumers = client.getConsumers(auth);

            return consumers.stream()
                .filter(consumer -> queueName.equals(((Map<String, String>) consumer.get("queue")).get("name")))
                .count();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Module getOpenPaasModule(WireMockOpenPaaSServerExtension openPaasServerExtension, RabbitMQExtension rabbitMQExtension) {
        return Modules.override(new OpenPaasModule(), new OpenPaasContactsConsumerModule())
            .with(new OpenPaasTestModule(openPaasServerExtension, Optional.empty(),
                Optional.of(contactConsumerConfigurationFunction.apply(rabbitMQExtension))));
    }
}