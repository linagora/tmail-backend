package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedJmapTest implements JmapContract {
    @RegisterExtension
    OpenpaasJamesDistributedExtension extension = new OpenpaasJamesDistributedExtension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
