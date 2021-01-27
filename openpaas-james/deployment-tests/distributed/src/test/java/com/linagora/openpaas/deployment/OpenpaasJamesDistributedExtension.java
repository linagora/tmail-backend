package com.linagora.openpaas.deployment;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.util.Port;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class OpenpaasJamesDistributedExtension implements BeforeEachCallback, AfterEachCallback {
    private static final int ONE_TIME = 1;

    private final Network network;
    private final GenericContainer<?> cassandra;
    private final GenericContainer<?> elasticsearch;
    private final GenericContainer<?> rabbitmq;
    private final GenericContainer<?> s3;
    private final GenericContainer<?> james;

    public OpenpaasJamesDistributedExtension() {
        network = Network.newNetwork();
        cassandra = createCassandra();
        elasticsearch = createElasticsearch();
        rabbitmq = createRabbitMQ();
        s3 = createS3();
        james = createOpenPaasJamesDistributed();
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createCassandra() {
        return new GenericContainer<>("cassandra:3.11.3")
            .withNetworkAliases("cassandra")
            .withNetwork(network)
            .withExposedPorts(9042);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createElasticsearch() {
        return new GenericContainer<>("docker.elastic.co/elasticsearch/elasticsearch:6.3.2")
            .withNetworkAliases("elasticsearch")
            .withNetwork(network)
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node");
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createRabbitMQ() {
        return new GenericContainer<>("rabbitmq:3.8.3-management")
            .withNetworkAliases("rabbitmq")
            .withNetwork(network)
            .withExposedPorts(5672, 15672);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createS3() {
        return new GenericContainer<>("zenko/cloudserver:8.2.6")
            .withNetworkAliases("s3", "s3.docker.test")
            .withNetwork(network)
            .withEnv("SCALITY_ACCESS_KEY_ID", "accessKey1")
            .withEnv("SCALITY_SECRET_ACCESS_KEY", "secretKey1")
            .withEnv("S3BACKEND", "mem")
            .withEnv("REMOTE_MANAGEMENT_DISABLE", "1");
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createOpenPaasJamesDistributed() {
        return new GenericContainer<>("linagora/openpaas-james-distributed:latest")
            .withNetworkAliases("james-distributed")
            .withNetwork(network)
            .dependsOn(cassandra, elasticsearch, s3, rabbitmq)
            .waitingFor(Wait.forLogMessage(".*JAMES server started.*\\n", ONE_TIME));
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        Runnables.runParallel(
            cassandra::start,
            elasticsearch::start,
            rabbitmq::start,
            s3::start);
        james.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        james.stop();
        Runnables.runParallel(
            cassandra::stop,
            elasticsearch::stop,
            rabbitmq::stop,
            s3::stop);
    }

    public GenericContainer<?> getContainer() {
        return james;
    }

    ExternalJamesConfiguration configuration() {
        return new ExternalJamesConfiguration() {
            @Override
            public String getAddress() {
                return james.getContainerIpAddress();
            }

            @Override
            public Port getImapPort() {
                return Port.of(james.getMappedPort(143));
            }

            @Override
            public Port getSmptPort() {
                return Port.of(james.getMappedPort(25));
            }
        };
    }
}
