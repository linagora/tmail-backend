package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraCalendarEventMaybeMethodContract;

public class PostgresLinagoraCalendarEventMaybeMethodTest implements LinagoraCalendarEventMaybeMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION.apply(new DelegationProbeModule())
        .build();

    @Override
    public String randomBlobId() {
        return TmailJmapBase.randomBlobId();
    }
}