package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LabelGetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;

public class PostgresLinagoraLabelGetMethodTest implements LabelGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapGuiceLabelModule())
        .build();
}