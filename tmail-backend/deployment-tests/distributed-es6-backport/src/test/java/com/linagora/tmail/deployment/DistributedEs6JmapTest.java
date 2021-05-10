package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedEs6JmapTest implements JmapContract {
    @RegisterExtension
    TmailDistributedEs6Extension extension = new TmailDistributedEs6Extension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
