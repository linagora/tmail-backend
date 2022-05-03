package com.linagora.tmail.james.app;

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.time.Duration;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueManagement;
import org.apache.james.queue.rabbitmq.view.RabbitMQMailQueueConfiguration;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.RabbitMQEmailAddressContactConfiguration;

public class TestRabbitMQModule extends AbstractModule {
    private static final int MAX_THREE_RETRIES = 3;
    private static final int MIN_DELAY_OF_ONE_HUNDRED_MILLISECONDS = 100;
    private static final int CONNECTION_TIMEOUT_OF_ONE_SECOND = 1000;
    private static final int CHANNEL_RPC_TIMEOUT_OF_ONE_SECOND = 1000;
    private static final int HANDSHAKE_TIMEOUT_OF_ONE_SECOND = 1000;
    private static final int SHUTDOWN_TIMEOUT_OF_ONE_SECOND = 1000;
    private static final int NETWORK_RECOVERY_INTERVAL_OF_ONE_SECOND = 1000;
    private static final String ADDRESS_CONTACT_QUEUE = "AddressContactQueueForTesting";

    private final DockerRabbitMQ rabbitMQ;

    public TestRabbitMQModule(DockerRabbitMQ rabbitMQ) {
        this.rabbitMQ = rabbitMQ;
    }

    @Override
    protected void configure() {
        bind(CassandraMailQueueViewConfiguration.class).toInstance(CassandraMailQueueViewConfiguration
            .builder()
            .bucketCount(1)
            .updateBrowseStartPace(1000)
            .sliceWindow(Duration.ofHours(1))
            .build());

        Multibinder.newSetBinder(binder(), CleanupTasksPerformer.CleanupTask.class)
            .addBinding()
            .to(QueueCleanUp.class);
    }

    @Provides
    @Singleton
    protected RabbitMQConfiguration provideRabbitMQConfiguration() throws URISyntaxException {
        return RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQ.amqpUri())
            .managementUri(rabbitMQ.managementUri())
            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
            .maxRetries(MAX_THREE_RETRIES)
            .minDelayInMs(MIN_DELAY_OF_ONE_HUNDRED_MILLISECONDS)
            .connectionTimeoutInMs(CONNECTION_TIMEOUT_OF_ONE_SECOND)
            .channelRpcTimeoutInMs(CHANNEL_RPC_TIMEOUT_OF_ONE_SECOND)
            .handshakeTimeoutInMs(HANDSHAKE_TIMEOUT_OF_ONE_SECOND)
            .shutdownTimeoutInMs(SHUTDOWN_TIMEOUT_OF_ONE_SECOND)
            .networkRecoveryIntervalInMs(NETWORK_RECOVERY_INTERVAL_OF_ONE_SECOND)
            .build();
    }

    @Provides
    @Singleton
    private RabbitMQMailQueueConfiguration getMailQueueSizeConfiguration() {
        return RabbitMQMailQueueConfiguration.sizeMetricsEnabled();
    }

    @Provides
    @Singleton
    public RabbitMQEmailAddressContactConfiguration rabbitMQEmailAddressContactConfiguration(RabbitMQConfiguration rabbitMQConfiguration) {
        return new RabbitMQEmailAddressContactConfiguration(ADDRESS_CONTACT_QUEUE,
            rabbitMQConfiguration.getUri(),
            rabbitMQConfiguration.getManagementCredentials());
    }

    public static class QueueCleanUp implements CleanupTasksPerformer.CleanupTask {
        private final RabbitMQMailQueueManagement api;

        @Inject
        public QueueCleanUp(RabbitMQMailQueueManagement api) {
            this.api = api;
        }

        @Override
        public Result run() {
            api.deleteAllQueues();

            return Result.COMPLETED;
        }
    }
}
