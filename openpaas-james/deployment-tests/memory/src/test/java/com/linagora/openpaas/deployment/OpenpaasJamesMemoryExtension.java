package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class OpenpaasJamesMemoryExtension implements BeforeEachCallback, AfterEachCallback {
    private static final int ONE_TIME = 1;

    private final GenericContainer<?> container = new GenericContainer<>("linagora/openpaas-james-memory:latest")
        .waitingFor(Wait.forLogMessage(".*Web admin server started.*\\n", ONE_TIME));

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        container.stop();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        container.start();
    }

    public GenericContainer<?> getContainer() {
        return container;
    }
}
