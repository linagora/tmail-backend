package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.draft.JMAPDraftConfiguration;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
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
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .overrideServerModule(binder -> binder.bind(JMAPDraftConfiguration.class)
            .toInstance(TestJMAPServerModule
                .jmapDraftConfigurationBuilder()
                .jwtPublicKeyPem(ImmutableList.of(LinagoraTestJMAPServerModule.JWT_PUBLIC_PEM_KEY))
                .build()))
        .build();
}
