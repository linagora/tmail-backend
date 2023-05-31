package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.ClockExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.DeletedMessageVaultProbeModule;
import com.linagora.tmail.james.common.EmailRecoveryActionIntegrationTest;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import org.apache.james.modules.vault.TestDeleteMessageVaultPreDeletionHookModule;

public class MemoryEmailRecoveryActionIntegrationTest implements EmailRecoveryActionIntegrationTest {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new DeletedMessageVaultProbeModule())
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new TestDeleteMessageVaultPreDeletionHookModule()))
        .extension(new ClockExtension())
        .build();
}
