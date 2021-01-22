package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class MemoryJmapTest implements JmapContract {
    @RegisterExtension
    static OpenpaasJamesMemoryExtension extension = new OpenpaasJamesMemoryExtension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
