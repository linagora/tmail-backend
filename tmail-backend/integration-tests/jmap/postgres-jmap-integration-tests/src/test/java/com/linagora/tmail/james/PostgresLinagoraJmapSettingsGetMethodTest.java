package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.JmapSettingsGetMethodContract;
import com.linagora.tmail.james.common.probe.JmapSettingsProbeModule;

public class PostgresLinagoraJmapSettingsGetMethodTest implements JmapSettingsGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapSettingsProbeModule())
        .build();
}