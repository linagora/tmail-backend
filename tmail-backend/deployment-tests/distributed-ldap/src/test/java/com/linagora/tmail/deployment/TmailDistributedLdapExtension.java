package com.linagora.tmail.deployment;

import static com.linagora.tmail.deployment.ThirdPartyContainers.OS_IMAGE_NAME;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createCassandra;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createElasticsearch;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createRabbitMQ;
import static com.linagora.tmail.deployment.ThirdPartyContainers.createS3;

import java.util.UUID;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.util.Port;
import org.apache.james.util.Runnables;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

public class TmailDistributedLdapExtension implements BeforeEachCallback, AfterEachCallback {

    private final Network network;
    private final GenericContainer<?> cassandra;
    private final GenericContainer<?> elasticsearch;
    private final GenericContainer<?> rabbitmq;
    private final GenericContainer<?> s3;
    private final GenericContainer<?> james;
    private final GenericContainer<?> ldap;

    public TmailDistributedLdapExtension() {
        network = Network.newNetwork();
        cassandra = createCassandra(network);
        elasticsearch = createElasticsearch(network, OS_IMAGE_NAME);
        rabbitmq = createRabbitMQ(network);
        s3 = createS3(network);
        ldap = createLdap(network);
        james = createTmailDistributedLdap();
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createLdap(Network network) {
        return new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromClasspath("populate.ldif", "prepopulated-ldap/populate.ldif")
                .withFileFromClasspath("Dockerfile", "prepopulated-ldap/Dockerfile"))
            .withNetworkAliases("ldap")
            .withNetwork(network)
            .withEnv("SLAPD_DOMAIN", "james.org")
            .withEnv("SLAPD_PASSWORD", "mysecretpassword")
            .withEnv("SLAPD_CONFIG_PASSWORD", "mysecretpassword")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("tmail-openldap-testing" + UUID.randomUUID()));
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createTmailDistributedLdap() {
        return new GenericContainer<>("linagora/tmail-backend-distributed:latest")
            .withNetworkAliases("james-distributed-ldap")
            .withNetwork(network)
            .dependsOn(cassandra, elasticsearch, s3, rabbitmq, ldap)
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/usersrepository.xml"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/imapserver.xml"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jwt_privatekey"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jwt_publickey"), "/root/conf/")
            .withCreateContainerCmdModifier(cmder -> cmder.withName("tmail-distributed-ldap-testing" + UUID.randomUUID()))
            .waitingFor(TestContainerWaitStrategy.WAIT_STRATEGY)
            .withExposedPorts(25, 143, 80);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        Runnables.runParallel(
            cassandra::start,
            elasticsearch::start,
            rabbitmq::start,
            s3::start,
            ldap::start);
        james.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        james.stop();
        Runnables.runParallel(
            cassandra::stop,
            elasticsearch::stop,
            rabbitmq::stop,
            s3::stop,
            ldap::stop);
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
