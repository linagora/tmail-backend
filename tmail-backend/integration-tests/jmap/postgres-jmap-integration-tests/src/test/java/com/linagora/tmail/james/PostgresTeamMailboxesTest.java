package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.webadmin.RandomPortSupplier;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;
import com.linagora.tmail.james.common.TeamMailboxesContract;
import com.linagora.tmail.team.TeamMailboxProbe;

public class PostgresTeamMailboxesTest implements TeamMailboxesContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(Modules.combine(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.builder()
                .port(new RandomPortSupplier())
                .enabled()
                .host("127.0.0.1")
                .build()),
            binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(TeamMailboxProbe.class)))
        .build();

    @Override
    @Disabled("Hanging. TODO investigate")
    public void webSocketShouldPushTeamMailboxStateChanges(GuiceJamesServer server) {

    }
}