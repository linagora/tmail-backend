package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class MemoryCliTest implements CliContract {
    @RegisterExtension
    TmailMemoryExtension extension = new TmailMemoryExtension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
