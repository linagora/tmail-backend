package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class MemoryJmapTest implements JmapContract {
    @RegisterExtension
    static TmailMemoryExtension extension = new TmailMemoryExtension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
