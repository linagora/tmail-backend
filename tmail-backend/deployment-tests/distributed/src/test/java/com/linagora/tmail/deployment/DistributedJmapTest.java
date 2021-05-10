package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedJmapTest implements JmapContract {
    @RegisterExtension
    TmailDistributedExtension extension = new TmailDistributedExtension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
