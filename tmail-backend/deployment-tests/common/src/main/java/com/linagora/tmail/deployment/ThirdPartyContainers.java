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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class ThirdPartyContainers {
    public static String OS_IMAGE_NAME = "opensearchproject/opensearch:2.19.2";
    public static String OS_NETWORK_ALIAS = "opensearch";

    @SuppressWarnings("resource")
    public static GenericContainer<?> createCassandra(Network network) {
        return new GenericContainer<>("cassandra:4.1.5")
            .withNetworkAliases("cassandra")
            .withNetwork(network)
            .withEnv("JVM_OPTS", "-Dcassandra.skip_wait_for_gossip_to_settle=0 -Dcassandra.initial_token=1")
            .withExposedPorts(9042)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("twake-mail-cassandra-testing" + UUID.randomUUID()))
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*Startup complete\\n").withTimes(1)
                .withStartupTimeout(Duration.ofMinutes(5)));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createSearchContainer(Network network, String imageName, String networkAlias) {
        return new GenericContainer<>(imageName)
            .withNetworkAliases(networkAlias)
            .withNetwork(network)
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .withEnv("DISABLE_SECURITY_PLUGIN", "true")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("twake-mail-search-testing" + UUID.randomUUID()))
            .waitingFor(Wait.forHttp("/_cluster/health?pretty=true")
                .forPort(9200)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createRabbitMQ(Network network) {
        return new GenericContainer<>("rabbitmq:3.13.3-management")
            .withNetworkAliases("rabbitmq")
            .withNetwork(network)
            .withExposedPorts(5672, 15672)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("twake-mail-rabbitmq-testing" + UUID.randomUUID()))
            .waitingFor(Wait.forHttp("/api/health/checks/alarms")
                .withBasicCredentials("guest", "guest")
                .forPort(15672)
                .forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createS3(Network network) {
        return new GenericContainer<>("registry.scality.com/cloudserver/cloudserver:8.7.25")
            .withNetworkAliases("s3", "s3.docker.test")
            .withNetwork(network)
            .withEnv("SCALITY_ACCESS_KEY_ID", "accessKey1")
            .withEnv("SCALITY_SECRET_ACCESS_KEY", "secretKey1")
            .withEnv("S3BACKEND", "mem")
            .withEnv("REMOTE_MANAGEMENT_DISABLE", "1")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("twake-mail-s3-testing" + UUID.randomUUID()))
            .waitingFor(new LogMessageWaitStrategy().withRegEx(".*server started.*").withTimes(1)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createRedis(Network network) {
        return new GenericContainer<>(DockerImageName.parse("redis").withTag("7.2.5"))
            .withExposedPorts(6379)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("twake-mail-redis-testing" + UUID.randomUUID()))
            .withCommand("--loglevel", "debug")
            .withNetworkAliases("redis")
            .withNetwork(network)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
    }
}
