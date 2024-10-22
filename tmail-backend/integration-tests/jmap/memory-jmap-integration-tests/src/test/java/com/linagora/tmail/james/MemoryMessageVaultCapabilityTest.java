package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.MemoryMessageVaultCapabilityContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.method.EmailRecoveryActionConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryMessageVaultCapabilityTest {

    @Nested
    class EmailRecoveryConfiguredTest implements MemoryMessageVaultCapabilityContract.EmailRecoveryConfigured {
        @RegisterExtension
        static JamesServerExtension
                jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
                MemoryConfiguration.builder()
                        .workingDirectory(tmpDir)
                        .configurationFromClasspath()
                        .usersRepository(DEFAULT)
                        .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                        .build())
                .server(configuration -> MemoryServer.createServer(configuration)
                        .overrideWith(new LinagoraTestJMAPServerModule()))
                .build();
    }

    @Nested
    class EmailRecoveryNotConfiguredTest implements MemoryMessageVaultCapabilityContract.EmailRecoveryNotConfigured {
        @RegisterExtension
        static JamesServerExtension
                jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
                MemoryConfiguration.builder()
                        .workingDirectory(tmpDir)
                        .configurationFromClasspath()
                        .usersRepository(DEFAULT)
                        .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                        .build())
                .server(configuration -> MemoryServer.createServer(configuration)
                        .overrideWith(new LinagoraTestJMAPServerModule(),
                                binder -> binder.bind(EmailRecoveryActionConfiguration.class)
                                        .toInstance(new EmailRecoveryActionConfiguration())
                        ))
                .build();
    }
}