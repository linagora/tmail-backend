package com.linagora.tmail.deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedLdapJmapTest implements JmapContract {
    @RegisterExtension
    TmailDistributedLdapExtension extension = new TmailDistributedLdapExtension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
