package com.linagora.openpaas.james.app;

import static org.apache.james.JamesServerMain.LOGGER;
import static org.apache.james.MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE;

import org.apache.james.FakeSearchMailboxModule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.server.core.configuration.Configuration;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.openpaas.james.jmap.CustomMethodModule;

public class MemoryServer {
    public static final Module MODULES = Modules.combine(
        IN_MEMORY_SERVER_AGGREGATE_MODULE,
       new CustomMethodModule());

    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Loading configuration {}", configuration.toString());
        GuiceJamesServer server = createServer(configuration)
            .combineWith(new FakeSearchMailboxModule(), new JMXServerModule());

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(Configuration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(MODULES);
    }
}
