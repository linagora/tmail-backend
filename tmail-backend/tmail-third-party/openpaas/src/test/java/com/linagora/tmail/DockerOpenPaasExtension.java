package com.linagora.tmail;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;


public class DockerOpenPaasExtension implements ParameterResolver, BeforeEachCallback,
    AfterEachCallback {
    private static final DockerOpenPaasContainer DOCKER_OPEN_PAAS_SINGLETON = new DockerOpenPaasContainer();

    public static DockerOpenPaasContainer getDockerOpenPaasSingleton() {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws
        ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerOpenPaasContainer.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.stop();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.start();
    }
}
