package com.linagora.tmail.james;

import java.util.Optional;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraShortLivedTokenRoutesContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryShortLivedTokenRoutesTest implements LinagoraShortLivedTokenRoutesContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .overrideServerModule(binder -> binder.bind(JMAPDraftConfiguration.class)
            .toInstance(TestJMAPServerModule
                .jmapDraftConfigurationBuilder()
                .jwtPublicKeyPem(Optional.of(LinagoraTestJMAPServerModule.JWT_PUBLIC_PEM_KEY))
                .build()))
        .build();
}
