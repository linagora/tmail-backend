package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedEs6CliTest implements CliContract {
    @RegisterExtension
    OpenpaasJamesDistributedEs6Extension extension = new OpenpaasJamesDistributedEs6Extension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
