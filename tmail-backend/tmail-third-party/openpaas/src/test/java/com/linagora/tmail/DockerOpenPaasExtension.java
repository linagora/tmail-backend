package com.linagora.tmail;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DockerOpenPaasExtension implements ParameterResolver, BeforeAllCallback,
    AfterAllCallback{

    private static final DockerOpenPaasSetup DOCKER_OPEN_PAAS_SINGLETON = new DockerOpenPaasSetup();
    private static final int OPEN_PASS_TEST_USERS_COUNT = 20;

    private DockerOpenPaasPopulateService dockerOpenPaasPopulateService = new DockerOpenPaasPopulateService();

    public static DockerOpenPaasSetup getDockerOpenPaasSingleton() {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws
        ParameterResolutionException {
        return (parameterContext.getParameter().getType() == DockerOpenPaasSetup.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.stop();
    }

    public DockerOpenPaasSetup getDockerOpenPaasSetup() {
        return DOCKER_OPEN_PAAS_SINGLETON;
    }

    public OpenPaasUser newTestUser() {
        return dockerOpenPaasPopulateService.createUser()
            .map(OpenPaasUser::fromDocument)
            .block();
    }
}