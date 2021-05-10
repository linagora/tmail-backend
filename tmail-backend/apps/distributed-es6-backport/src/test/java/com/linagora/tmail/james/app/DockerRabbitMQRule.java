package com.linagora.tmail.james.app;

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;

import java.net.URISyntaxException;
import java.time.Duration;

import org.apache.james.CleanupTasksPerformer;
import org.apache.james.GuiceModuleTestRule;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.DockerRabbitMQSingleton;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import com.google.inject.Module;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;

public class DockerRabbitMQRule implements GuiceModuleTestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        return base;
    }

    @Override
    public Module getModule() {
        return Modules.combine((binder) -> {
                try {
                    binder.bind(RabbitMQConfiguration.class)
                        .toInstance(RabbitMQConfiguration.builder()
                            .amqpUri(DockerRabbitMQSingleton.SINGLETON.amqpUri())
                            .managementUri(DockerRabbitMQSingleton.SINGLETON.managementUri())
                            .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                            .build());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            },
            binder -> binder.bind(ReactorRabbitMQChannelPool.Configuration.class)
                .toInstance(ReactorRabbitMQChannelPool.Configuration.builder()
                    .retries(2)
                    .minBorrowDelay(Duration.ofMillis(5))
                    .maxChannel(3)),
            binder -> binder.bind(SimpleConnectionPool.Configuration.class)
                .toInstance(SimpleConnectionPool.Configuration.builder()
                    .retries(2)
                    .initialDelay(Duration.ofMillis(5))),
            binder -> Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class)
                .addBinding()
                .to(TestRabbitMQModule.QueueCleanUp.class));
    }

    public DockerRabbitMQ dockerRabbitMQ() {
        return DockerRabbitMQSingleton.SINGLETON;
    }

    public void start() {
        DockerRabbitMQSingleton.SINGLETON.start();
    }

    public void stop() {
    }
}
