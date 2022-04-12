package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraContactAutocompleteMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceContactAutocompleteModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryLinagoraContactAutocompleteMethodTest implements LinagoraContactAutocompleteMethodContract {
    @RegisterExtension
    static JamesServerExtension
        jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapGuiceContactAutocompleteModule()))
        .build();

    @Override
    public void awaitDocumentsIndexed(long documentCount) {

    }
}
