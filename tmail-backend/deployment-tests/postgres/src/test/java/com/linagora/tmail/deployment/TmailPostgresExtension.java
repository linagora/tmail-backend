package com.linagora.tmail.deployment;

import static com.linagora.tmail.deployment.ThirdPartyContainers.OS_IMAGE_NAME;
import static com.linagora.tmail.deployment.ThirdPartyContainers.OS_NETWORK_ALIAS;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createRabbitMQ;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createS3;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createSearchContainer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.util.Port;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.MountableFile;

public class TmailPostgresExtension implements BeforeEachCallback, AfterEachCallback {
    private final Network network;
    private final GenericContainer<?> postgres;
    private final GenericContainer<?> opensearch;
    private final GenericContainer<?> rabbitmq;
    private final GenericContainer<?> s3;
    private final GenericContainer<?> tmailBackend;

    public TmailPostgresExtension() {
        network = Network.newNetwork();
        postgres = createPostgres(network);
        opensearch = createSearchContainer(network, OS_IMAGE_NAME, OS_NETWORK_ALIAS);
        rabbitmq = createRabbitMQ(network);
        s3 = createS3(network);
        tmailBackend = createTmailDistributed();
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createTmailDistributed() {
        return new GenericContainer<>("linagora/tmail-backend-postgresql-experimental:latest")
            .withNetworkAliases("tmail-postgres")
            .withNetwork(network)
            .dependsOn(postgres, opensearch, s3, rabbitmq)
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/imapserver.xml"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jwt_privatekey"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jwt_publickey"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/keystore"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jmxremote.password"), "/root/conf/")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-postgres-testing" + UUID.randomUUID()))
            .waitingFor(TestContainerWaitStrategy.WAIT_STRATEGY)
            .withExposedPorts(25, 143, 80, 8000);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createPostgres(Network network) {
        return new GenericContainer<>("postgres:16.1")
            .withNetworkAliases("postgres")
            .withNetwork(network)
            .withEnv("POSTGRES_USER", "tmail")
            .withEnv("POSTGRES_PASSWORD", "secret1")
            .withEnv("POSTGRES_DB", "postgres")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-postgres-testing" + UUID.randomUUID()))
            .waitingFor((new LogMessageWaitStrategy()).withRegEx(".*database system is ready to accept connections.*\\s").withTimes(2).withStartupTimeout(Duration.of(60L, ChronoUnit.SECONDS)))
            .withExposedPorts(5432);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        String dockerSaveFileUrl = new File("").getAbsolutePath().replace(Paths.get("tmail-backend", "deployment-tests", "postgres").toString(),
            Paths.get("tmail-backend", "apps", "postgres", "target", "jib-image.tar").toString());
        tmailBackend.getDockerClient().loadImageCmd(Files.newInputStream(Paths.get(dockerSaveFileUrl))).exec();
        tmailBackend.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        tmailBackend.stop();
        Runnables.runParallel(
            postgres::stop,
            opensearch::stop,
            rabbitmq::stop,
            s3::stop);
    }

    public GenericContainer<?> getContainer() {
        return tmailBackend;
    }

    ExternalJamesConfiguration configuration() {
        return new ExternalJamesConfiguration() {
            @Override
            public String getAddress() {
                return tmailBackend.getHost();
            }

            @Override
            public Port getImapPort() {
                return Port.of(tmailBackend.getMappedPort(143));
            }

            @Override
            public Port getSmptPort() {
                return Port.of(tmailBackend.getMappedPort(25));
            }
        };
    }

}
