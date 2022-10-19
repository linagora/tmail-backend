package com.linagora.tmail.deployment;

import java.util.UUID;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class ThirdPartyContainers {
    public static String ES6_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch:6.3.2";
    public static String OS_IMAGE_NAME = "opensearchproject/opensearch:2.1.0";
    public static String ES6_NETWORK_ALIAS = "elasticsearch";
    public static String OS_NETWORK_ALIAS = "opensearch";

    @SuppressWarnings("resource")
    public static GenericContainer<?> createCassandra(Network network) {
        return new GenericContainer<>("cassandra:3.11.10")
            .withNetworkAliases("cassandra")
            .withNetwork(network)
            .withExposedPorts(9042)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-cassandra-testing" + UUID.randomUUID()));
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
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-search-testing" + UUID.randomUUID()));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createRabbitMQ(Network network) {
        return new GenericContainer<>("rabbitmq:3.9.18-management")
            .withNetworkAliases("rabbitmq")
            .withNetwork(network)
            .withExposedPorts(5672, 15672)
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-rabbitmq-testing" + UUID.randomUUID()));
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createS3(Network network) {
        return new GenericContainer<>("zenko/cloudserver:8.2.6")
            .withNetworkAliases("s3", "s3.docker.test")
            .withNetwork(network)
            .withEnv("SCALITY_ACCESS_KEY_ID", "accessKey1")
            .withEnv("SCALITY_SECRET_ACCESS_KEY", "secretKey1")
            .withEnv("S3BACKEND", "mem")
            .withEnv("REMOTE_MANAGEMENT_DISABLE", "1")
            .withCreateContainerCmdModifier(createContainerCmd -> createContainerCmd.withName("team-mail-s3-testing" + UUID.randomUUID()));
    }
}
