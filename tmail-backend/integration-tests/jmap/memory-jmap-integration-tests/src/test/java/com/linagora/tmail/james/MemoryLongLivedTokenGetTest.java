package com.linagora.tmail.james;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.modules.TestJMAPServerModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LongLivedTokenGetContract;

public class MemoryLongLivedTokenGetTest implements LongLivedTokenGetContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new TestJMAPServerModule()))
        .build();
}
