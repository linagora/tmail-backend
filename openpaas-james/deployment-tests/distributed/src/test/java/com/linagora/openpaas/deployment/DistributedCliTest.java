package com.linagora.openpaas.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedCliTest implements CliContract {
    @RegisterExtension
    OpenpaasJamesDistributedExtension extension = new OpenpaasJamesDistributedExtension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
