package com.linagora.tmail.james.app;

import static com.linagora.tmail.ScheduledReconnectionHandler.QUEUES_TO_MONITOR;
import static com.linagora.tmail.ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration.ENABLED;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.vault.VaultConfiguration;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.OpenPaasContactsConsumerModule;
import com.linagora.tmail.OpenPaasModule;
import com.linagora.tmail.ScheduledReconnectionHandler;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;

class SchedulerReconnectionHandlerIntegrationTest {
    private static final Duration FAST_RECONNECTION_HANDLER_INTERVAL = Duration.ofSeconds(10);
    private static final String DELETED_MESSAGE_VAULT_QUEUE = "deleted-message-vault-work-queue";
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @Nested
    class DeletedMessageVaultQueueEnabled {
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
                .mailbox(new MailboxConfiguration(false))
                .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
                .vaultConfiguration(VaultConfiguration.ENABLED_WORKQUEUE)
                .build())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(new OpenPaasModule(), new OpenPaasContactsConsumerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(DeletedMessageVaultWorkQueueProbe.class))
                .overrideWith(binder -> binder.bind(ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration.class)
                    .toInstance(new ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration(ENABLED, FAST_RECONNECTION_HANDLER_INTERVAL)))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();

        @BeforeEach
        void setUp(DockerRabbitMQ dockerRabbitMQ) {
            // ensure all the consumers for vital queues started well
            CALMLY_AWAIT.atMost(10, SECONDS)
                .untilAsserted(() -> SoftAssertions.assertSoftly(softly -> QUEUES_TO_MONITOR
                    .forEach(queueName -> softly.assertThat(consumerCount(dockerRabbitMQ, queueName))
                    .isGreaterThanOrEqualTo(1L))));
        }

        @Test
        void shouldRestartDeletedMessageVaultConsumerIfConsumerDisconnected(GuiceJamesServer jamesServer, DockerRabbitMQ dockerRabbitMQ) {
            // Disconnect consumer of `deleted-message-vault-work-queue`
            jamesServer.getProbe(DeletedMessageVaultWorkQueueProbe.class).stop();
            assertThat(consumerCount(dockerRabbitMQ, DELETED_MESSAGE_VAULT_QUEUE))
                .isZero();

            // Await for `deleted-message-vault-work-queue` consumer to reconnect
            CALMLY_AWAIT.atMost(20, SECONDS)
                .untilAsserted(() -> assertThat(consumerCount(dockerRabbitMQ, DELETED_MESSAGE_VAULT_QUEUE))
                    .isEqualTo(1L));
        }

        @Test
        void allMonitoredConsumersShouldStayHealthyAfterAReconnectionTriggered(GuiceJamesServer jamesServer, DockerRabbitMQ dockerRabbitMQ) {
            // Disconnect consumer of `deleted-message-vault-work-queue`
            jamesServer.getProbe(DeletedMessageVaultWorkQueueProbe.class).stop();
            assertThat(consumerCount(dockerRabbitMQ, DELETED_MESSAGE_VAULT_QUEUE))
                .isZero();

            // All consumers should stay healthy after the reconnection caused by `deleted-message-vault-work-queue`
            CALMLY_AWAIT.atMost(20, SECONDS)
                .untilAsserted(() -> SoftAssertions.assertSoftly(softly ->
                    QUEUES_TO_MONITOR.forEach(queueName -> softly.assertThat(consumerCount(dockerRabbitMQ, queueName))
                        .isGreaterThanOrEqualTo(1L))));
        }
    }

    @Nested
    class DeletedMessageVaultQueueDisabled {
        public static final List<String> QUEUES_TO_MONITOR_EXCEPT_DELETED_MESSAGE_VAULT = QUEUES_TO_MONITOR
            .stream()
            .filter(s -> !s.equals(DELETED_MESSAGE_VAULT_QUEUE))
            .toList();

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
                .mailbox(new MailboxConfiguration(false))
                .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
                .vaultConfiguration(VaultConfiguration.DEFAULT)
                .build())
            .server(configuration -> DistributedServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule())
                .overrideWith(new OpenPaasModule(), new OpenPaasContactsConsumerModule())
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(ScheduledReconnectionHandlerProbe.class))
                .overrideWith(binder -> binder.bind(ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration.class)
                    .toInstance(new ScheduledReconnectionHandler.ScheduledReconnectionHandlerConfiguration(ENABLED, FAST_RECONNECTION_HANDLER_INTERVAL)))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class)))
            .extension(new DockerOpenSearchExtension())
            .extension(new CassandraExtension())
            .extension(new RabbitMQExtension())
            .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
            .build();

        @BeforeEach
        void setUp(DockerRabbitMQ dockerRabbitMQ) {
            // ensure all the consumers for vital queues started well
            CALMLY_AWAIT.atMost(10, SECONDS)
                .untilAsserted(() -> SoftAssertions.assertSoftly(softly -> QUEUES_TO_MONITOR_EXCEPT_DELETED_MESSAGE_VAULT
                    .forEach(queueName -> softly.assertThat(consumerCount(dockerRabbitMQ, queueName))
                    .isGreaterThanOrEqualTo(1L))));
        }

        @Test
        void nonExistingQueueInTheMonitorListShouldNotTriggerReconnection(GuiceJamesServer jamesServer) {
            // "deleted-message-vault-work-queue" does not exist but should not trigger reconnection
            assertThat(jamesServer.getProbe(ScheduledReconnectionHandlerProbe.class)
                .restartNeeded())
                .isFalse();
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
}