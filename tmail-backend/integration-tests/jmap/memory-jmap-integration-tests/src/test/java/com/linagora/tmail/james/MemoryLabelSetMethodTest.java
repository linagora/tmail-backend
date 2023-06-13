package com.linagora.tmail.james;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LabelSetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

public class MemoryLabelSetMethodTest implements LabelSetMethodContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                    .workingDirectory(tmpDir)
                    .configurationFromClasspath()
                    .usersRepository(DEFAULT)
                    .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                    .overrideWith(new LinagoraTestJMAPServerModule())
                    .overrideWith(new DelegationProbeModule())
                    .overrideWith(new JmapGuiceLabelModule()))
            .build();
}
