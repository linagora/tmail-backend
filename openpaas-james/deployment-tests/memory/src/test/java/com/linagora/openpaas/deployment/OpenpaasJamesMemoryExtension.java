package com.linagora.openpaas.deployment;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.util.Port;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class OpenpaasJamesMemoryExtension implements BeforeEachCallback, AfterEachCallback {
    private static final int ONE_TIME = 1;

    private final GenericContainer<?> container = new GenericContainer<>("linagora/openpaas-james-memory:latest")
        .waitingFor(Wait.forLogMessage(".*JAMES server started.*\\n", ONE_TIME));

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        container.start();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        container.stop();
    }

    public GenericContainer<?> getContainer() {
        return container;
    }

    ExternalJamesConfiguration configuration() {
        return new ExternalJamesConfiguration() {
            @Override
            public String getAddress() {
                return container.getContainerIpAddress();
            }

            @Override
            public Port getImapPort() {
                return Port.of(container.getMappedPort(143));
            }

            @Override
            public Port getSmptPort() {
                return Port.of(container.getMappedPort(25));
            }
        };
    }
}
