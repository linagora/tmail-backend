package com.linagora.openpaas.james.app;

import static org.apache.james.JamesServerMain.LOGGER;
import static org.apache.james.MemoryJamesServerMain.IN_MEMORY_SERVER_MODULE;
import static org.apache.james.MemoryJamesServerMain.JMAP;
import static org.apache.james.MemoryJamesServerMain.WEBADMIN;

import org.apache.james.FakeSearchMailboxModule;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.JMXServerModule;
import org.apache.james.modules.spamassassin.SpamAssassinListenerModule;
import org.apache.james.server.core.configuration.Configuration;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.openpaas.james.jmap.method.CustomMethodModule;
import com.linagora.openpaas.james.jmap.method.FilterGetMethodModule;
import com.linagora.openpaas.james.jmap.method.FilterSetMethodModule;

public class MemoryServer {
    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    public static final Module IN_MEMORY_SERVER_AGGREGATE_MODULE = Modules.combine(
        IN_MEMORY_SERVER_MODULE,
        PROTOCOLS,
        JMAP,
        WEBADMIN,
        new DKIMMailetModule(),
        new SpamAssassinListenerModule());

    public static final Module MODULES = Modules.combine(
        IN_MEMORY_SERVER_AGGREGATE_MODULE,
        new CustomMethodModule(),
        new FilterGetMethodModule(),
        new FilterSetMethodModule());

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
