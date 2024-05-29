package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.core.JmapRfc8621Configuration;
import org.apache.james.jmap.rfc8621.contract.IdentityProbeModule;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.PublicAssetSetMethodContract;
import com.linagora.tmail.james.common.probe.PublicAssetProbeModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryPublicAssetSetSetMethodTest implements PublicAssetSetMethodContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new IdentityProbeModule())
            .overrideWith(new DelegationProbeModule())
            .overrideWith(new PublicAssetProbeModule())
            .overrideWith(binder -> binder.bind(JmapRfc8621Configuration.class)
                .toInstance(PublicAssetSetMethodContract.CONFIGURATION())))
        .build();
}