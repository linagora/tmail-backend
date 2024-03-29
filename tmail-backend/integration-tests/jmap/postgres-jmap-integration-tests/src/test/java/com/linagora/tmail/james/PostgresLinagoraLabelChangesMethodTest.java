package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.postgres.change.PostgresStateFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;

public class PostgresLinagoraLabelChangesMethodTest implements LabelChangesMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapGuiceLabelModule())
        .build();

    @Override
    public State.Factory stateFactory() {
        return new PostgresStateFactory();
    }
}