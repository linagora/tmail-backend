package com.linagora.openpaas.deployment;

import static com.linagora.openpaas.deployment.ThirdPartyContainers.ES7_IMAGE_NAME;
import static com.linagora.openpaas.deployment.ThirdPartyContainers.createCassandra;
import static com.linagora.openpaas.deployment.ThirdPartyContainers.createElasticsearch;
import static com.linagora.openpaas.deployment.ThirdPartyContainers.createRabbitMQ;
import static com.linagora.openpaas.deployment.ThirdPartyContainers.createS3;

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
        cassandra = createCassandra(network);
        elasticsearch = createElasticsearch(network, ES7_IMAGE_NAME);
        rabbitmq = createRabbitMQ(network);
        s3 = createS3(network);
        james = createOpenPaasJamesDistributed();
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createOpenPaasJamesDistributed() {
        return new GenericContainer<>("linagora/tmail-backend-distributed:latest")
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
