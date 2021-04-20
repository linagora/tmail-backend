package com.linagora.openpaas.james;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.openpaas.encrypted.MailboxConfiguration;
import com.linagora.openpaas.james.app.MemoryConfiguration;
import com.linagora.openpaas.james.app.MemoryServer;
import com.linagora.openpaas.james.common.LinagoraEchoMethodContract;
import com.linagora.openpaas.james.common.LinagoraTicketAuthenticationContract;

public class MemoryTicketAuthenticationTest implements LinagoraTicketAuthenticationContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();
}
