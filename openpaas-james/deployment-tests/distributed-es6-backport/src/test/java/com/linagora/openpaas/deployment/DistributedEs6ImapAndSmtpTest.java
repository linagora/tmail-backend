package com.linagora.openpaas.deployment;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class DistributedEs6ImapAndSmtpTest extends ImapAndSmtpContract {
    @RegisterExtension
    OpenpaasJamesDistributedEs6Extension extension = new OpenpaasJamesDistributedEs6Extension();

    @Override
    protected ExternalJamesConfiguration configuration() {
        return extension.configuration();
    }

    @Override
    protected GenericContainer<?> container() {
        return extension.getContainer();
    }
}
