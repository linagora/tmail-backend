package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedCliTest implements CliContract {
    @RegisterExtension
    TmailDistributedExtension extension = new TmailDistributedExtension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
