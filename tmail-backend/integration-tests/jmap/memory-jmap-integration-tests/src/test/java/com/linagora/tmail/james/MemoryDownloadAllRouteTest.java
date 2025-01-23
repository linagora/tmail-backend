package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.DownloadAllContract;
import com.linagora.tmail.james.common.LabelSetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryDownloadAllRouteTest implements DownloadAllContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                    .workingDirectory(tmpDir)
                    .configurationFromClasspath()
                    .usersRepository(DEFAULT)
                    .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                    .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                    .overrideWith(new LinagoraTestJMAPServerModule())
                    .overrideWith(new DelegationProbeModule())
                    .overrideWith(new JmapGuiceLabelModule()))
            .build();

    @Override
    public MessageId randomMessageId() {
        return InMemoryMessageId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }
}
