package com.linagora.tmail;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;

import com.google.inject.Module;

public class OpenPaasRabbitMQExtension implements GuiceModuleTestExtension {
    private final DockerRabbitMQ dockerRabbitMQ = DockerRabbitMQ.withoutCookie();

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        dockerRabbitMQ.stop();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        dockerRabbitMQ.start();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws
        ParameterResolutionException {
        return parameterContext.getParameter().getType() == DockerRabbitMQ.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return dockerRabbitMQ;
    }

    @Override
    public Module getModule() {
        return new TestOpenPaasRabbitMQModule(dockerRabbitMQ);
    }

    public DockerRabbitMQ dockerRabbitMQ() {
        return dockerRabbitMQ;
    }
}
