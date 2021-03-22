package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedEs6JmapTest implements JmapContract {
    @RegisterExtension
    OpenpaasJamesDistributedEs6Extension extension = new OpenpaasJamesDistributedEs6Extension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
