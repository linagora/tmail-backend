package com.linagora.openpaas.deployment;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

public class MemoryImapAndSmtpTest extends ImapAndSmtpContract {
    @RegisterExtension
    static OpenpaasJamesMemoryExtension extension = new OpenpaasJamesMemoryExtension();

    @Override
    ExternalJamesConfiguration configuration() {
        return extension.configuration();
    }

    @Override
    GenericContainer<?> container() {
        return extension.getContainer();
    }
}
