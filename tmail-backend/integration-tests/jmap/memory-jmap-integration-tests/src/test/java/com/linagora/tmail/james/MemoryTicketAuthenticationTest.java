package com.linagora.tmail.james;

import java.util.List;
import java.util.Optional;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.com.google.common.collect.ImmutableList;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraTicketAuthenticationContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryTicketAuthenticationTest implements LinagoraTicketAuthenticationContract {
    private static Optional<List<String>> AUTH_LIST = Optional.of(ImmutableList.of(
        "JWTAuthenticationStrategy",
        "BasicAuthenticationStrategy",
        "com.linagora.tmail.james.jmap.ticket.TicketAuthenticationStrategy"
    ));

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> binder.bind(JmapRfc8621Configuration.class)
                .toInstance(JmapRfc8621Configuration.LOCALHOST_CONFIGURATION()
                    .withAuthenticationStrategies(AUTH_LIST))))
        .build();
}
