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

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

public class TmailLdapPostgresExtension extends TmailPostgresExtension {
    private GenericContainer<?> ldap;

    public TmailLdapPostgresExtension() {
        ldap = createLdap(network);
    }

    @SuppressWarnings("resource")
    @Override
    public GenericContainer<?> createTmailDistributed() {
        if (ldap == null) {
            ldap = createLdap(network);
        }

        return new GenericContainer<>("linagora/tmail-backend-postgresql-experimental:latest")
            .withNetworkAliases("tmail-postgres")
            .withNetwork(network)
            .dependsOn(postgres, opensearch, s3, rabbitmq, ldap)
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/imapserver.xml"), "/root/conf/")
            .withCopyFileToContainer(MountableFile.forClasspathResource("james-conf/usersrepository.xml"), "/root/conf/")
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

    private GenericContainer<?> createLdap(Network network) {
        return new GenericContainer<>(
            new ImageFromDockerfile()
                .withFileFromClasspath("populate.ldif", "prepopulated-ldap/populate.ldif")
                .withFileFromClasspath("Dockerfile", "prepopulated-ldap/Dockerfile"))
            .withNetworkAliases("ldap")
            .withNetwork(network)
            .withEnv("LDAP_DOMAIN", "james.org")
            .withEnv("LDAP_ADMIN_PASSWORD", "secret")
            .withCommand("--copy-service --loglevel debug")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-openldap-testing" + UUID.randomUUID()))
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*slapd starting\\n").withTimes(1)
                .withStartupTimeout(Duration.ofMinutes(3)))
            .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)));
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        super.afterEach(extensionContext);
        ldap.stop();
    }
}
