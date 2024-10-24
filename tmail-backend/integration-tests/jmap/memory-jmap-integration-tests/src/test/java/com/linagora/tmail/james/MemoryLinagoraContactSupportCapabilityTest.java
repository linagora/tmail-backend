package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraContactSupportCapabilityContract;
import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryLinagoraContactSupportCapabilityTest {
    @Nested
    class MailAddressConfiguredTest
        implements LinagoraContactSupportCapabilityContract.MailAddressConfigured {
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
    class MailAddressNotConfiguredTest
        implements LinagoraContactSupportCapabilityContract.MailAddressNotConfigured {
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
                    binder -> binder.bind(JMAPExtensionConfiguration.class)
                        .toInstance(new JMAPExtensionConfiguration())
                ))
            .build();
    }

}
