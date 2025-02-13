package com.linagora.tmail;

import java.io.File;
import java.net.URISyntaxException;
import java.time.Duration;

import static com.google.common.io.Resources.getResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

public class DockerOpenPaasContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(DockerOpenPaasContainer.class);

    private final ComposeContainer environment;

    {
        try {
            environment = new ComposeContainer(
                new File(getResource("/docker-openpaas-setup.yml").toURI()))
                .waitingFor("openpaas", Wait.forLogMessage("*Users currently connected*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        environment.start();
    }

    public void stop() {
        environment.stop();
    }

    public ContainerState getOpenPaasContainer() {
        return environment.getContainerByServiceName("openpaas").orElseThrow();
    }

    public ContainerState getRabbitMqContainer() {
        return environment.getContainerByServiceName("rabbitmq").orElseThrow();
    }

    public ContainerState getSabreDav() {
        return environment.getContainerByServiceName("sabre_dav").orElseThrow();
    }
}