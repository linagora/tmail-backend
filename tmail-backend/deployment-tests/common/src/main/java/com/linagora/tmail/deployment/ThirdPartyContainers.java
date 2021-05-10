package com.linagora.tmail.deployment;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class ThirdPartyContainers {
    public static String ES6_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch:6.3.2";
    public static String ES7_IMAGE_NAME = "docker.elastic.co/elasticsearch/elasticsearch:7.10.2";

    @SuppressWarnings("resource")
    public static GenericContainer<?> createCassandra(Network network) {
        return new GenericContainer<>("cassandra:3.11.3")
            .withNetworkAliases("cassandra")
            .withNetwork(network)
            .withExposedPorts(9042);
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createElasticsearch(Network network, String imageName) {
        return new GenericContainer<>(imageName)
            .withNetworkAliases("elasticsearch")
            .withNetwork(network)
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node");
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createRabbitMQ(Network network) {
        return new GenericContainer<>("rabbitmq:3.8.3-management")
            .withNetworkAliases("rabbitmq")
            .withNetwork(network)
            .withExposedPorts(5672, 15672);
    }

    @SuppressWarnings("resource")
    public static GenericContainer<?> createS3(Network network) {
        return new GenericContainer<>("zenko/cloudserver:8.2.6")
            .withNetworkAliases("s3", "s3.docker.test")
            .withNetwork(network)
            .withEnv("SCALITY_ACCESS_KEY_ID", "accessKey1")
            .withEnv("SCALITY_SECRET_ACCESS_KEY", "secretKey1")
            .withEnv("S3BACKEND", "mem")
            .withEnv("REMOTE_MANAGEMENT_DISABLE", "1");
    }
}
