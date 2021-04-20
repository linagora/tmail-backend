package com.linagora.openpaas.james;

import static org.apache.james.jmap.JMAPTestingConstants.BOB;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.openpaas.james.app.MemoryConfiguration;
import com.linagora.openpaas.james.app.MemoryServer;
import com.linagora.openpaas.james.common.LinagoraFilterGetMethodContract;
import com.linagora.openpaas.james.common.module.JmapGuiceCustomModule;

public class MemoryLinagoraFilterGetMethodTest implements LinagoraFilterGetMethodContract {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule())
            .overrideWith(new JmapGuiceCustomModule()))
        .build();

    @Override
    public String generateMailboxIdForUser() {
        return InMemoryId.of(1).toString();
    }

    @Override
    public Username generateUsername() {
        return BOB;
    }

    @Override
    public String generateAccountIdAsString() {
        return "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6";
    }

}
