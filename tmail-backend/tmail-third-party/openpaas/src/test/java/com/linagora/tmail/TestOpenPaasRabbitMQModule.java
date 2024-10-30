package com.linagora.tmail;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.inject.Singleton;

import org.apache.james.backends.rabbitmq.DockerRabbitMQ;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class TestOpenPaasRabbitMQModule extends AbstractModule {
    private final DockerRabbitMQ dockerRabbitMQ;

    public TestOpenPaasRabbitMQModule(DockerRabbitMQ dockerRabbitMQ) {
        this.dockerRabbitMQ = dockerRabbitMQ;
    }

    @Provides
    @Singleton
    public OpenPaasConfiguration provideOpenPaasConfiguration() throws URISyntaxException {
        return new OpenPaasConfiguration(
            AmqpUri.from(dockerRabbitMQ.amqpUri()).asOptional(),
            URI.create("http://localhost:8081"),
            "user",
            "password"
        );
    }
}
