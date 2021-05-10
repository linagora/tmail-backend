package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedLdapCliTest implements CliContract {
    @RegisterExtension
    TmailDistributedLdapExtension extension = new TmailDistributedLdapExtension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
