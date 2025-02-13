package com.linagora.tmail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class DockerOpenPaasExtension implements ParameterResolver, BeforeAllCallback,
    AfterAllCallback {

    private static final DockerOpenPaasContainer DOCKER_OPEN_PAAS_SINGLETON = new DockerOpenPaasContainer();
    private static final int OPEN_PASS_TEST_USERS_COUNT = 20;

    private static int currentTestUserIndex = 0;

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
    public void beforeAll(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.start();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        DOCKER_OPEN_PAAS_SINGLETON.stop();
    }

    public OpenPaasUser newTestUser() {
        if (currentTestUserIndex >= 20) {
            throw new IllegalStateException("No more test users available.");
        }
        return new OpenPaasUser("user" + currentTestUserIndex++ + "@open-paas.org", "secret");
    }

    public List<OpenPaasUser> allTestUsers() {
        List<OpenPaasUser> users = new ArrayList<>();
        for (int i = 0; i < OPEN_PASS_TEST_USERS_COUNT; i++) {
            users.add(new OpenPaasUser("user" + i + "@open-paas.org", "secret"));
        }

        return users;
    }
}