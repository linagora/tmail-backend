package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class MemoryCliTest implements CliContract {
    @RegisterExtension
    OpenpaasJamesMemoryExtension extension = new OpenpaasJamesMemoryExtension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
