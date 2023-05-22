package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.ClockExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.DeletedMessageVaultProbeModule;
import com.linagora.tmail.james.common.EmailRecoveryActionSetMethodContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryEmailRecoveryActionSetMethodTest implements EmailRecoveryActionSetMethodContract {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new DeletedMessageVaultProbeModule())
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .extension(new ClockExtension())
        .build();

    @Override
    public MessageId randomMessageId() {
        return InMemoryMessageId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }
}
