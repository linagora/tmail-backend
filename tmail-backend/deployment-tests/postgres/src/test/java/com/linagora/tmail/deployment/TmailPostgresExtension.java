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
    protected final Network network;
    protected final GenericContainer<?> postgres;
    protected final GenericContainer<?> opensearch;
    protected final GenericContainer<?> rabbitmq;
    protected final GenericContainer<?> s3;
    protected final GenericContainer<?> tmailBackend;

    public TmailPostgresExtension() {
        network = Network.newNetwork();
        postgres = createPostgres(network);
        opensearch = createSearchContainer(network, OS_IMAGE_NAME, OS_NETWORK_ALIAS);
        rabbitmq = createRabbitMQ(network);
        s3 = createS3(network);
        tmailBackend = createTmailDistributed();
    }

    @SuppressWarnings("resource")
    protected GenericContainer<?> createTmailDistributed() {
        return new GenericContainer<>("linagora/tmail-backend-postgresql-experimental:latest")
            .withNetworkAliases("tmail-postgres")
            .withNetwork(network)
            .dependsOn(postgres, opensearch, s3, rabbitmq)
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/imapserver.xml"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jwt_privatekey"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jwt_publickey"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/keystore"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/jmxremote.password"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/blob.properties"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/rabbitmq.properties"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/opensearch.properties"), "/root/conf/")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-backend-postgres-testing" + UUID.randomUUID()))
            .waitingFor(TestContainerWaitStrategy.WAIT_STRATEGY)
            .withExposedPorts(25, 143, 80, 8000);
    }

    @SuppressWarnings("resource")
    protected static GenericContainer<?> createPostgres(Network network) {
        return new GenericContainer<>("postgres:16.9")
            .withNetworkAliases("postgres")
            .withNetwork(network)
            .withEnv("POSTGRES_USER", "tmail")
            .withEnv("POSTGRES_PASSWORD", "secret1")
            .withEnv("POSTGRES_DB", "postgres")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-postgres-database-testing" + UUID.randomUUID()))
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
