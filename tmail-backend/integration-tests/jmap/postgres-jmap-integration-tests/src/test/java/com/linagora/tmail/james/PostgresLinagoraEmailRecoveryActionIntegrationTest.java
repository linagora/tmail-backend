package com.linagora.tmail.james;

import org.apache.james.JamesServerExtension;
import org.apache.james.modules.vault.TestDeleteMessageVaultPreDeletionHookModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.util.Modules;
import com.linagora.tmail.james.common.DeletedMessageVaultProbeModule;
import com.linagora.tmail.james.common.EmailRecoveryActionIntegrationTest;

public class PostgresLinagoraEmailRecoveryActionIntegrationTest implements EmailRecoveryActionIntegrationTest {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(Modules.combine(new DeletedMessageVaultProbeModule(), new TestDeleteMessageVaultPreDeletionHookModule()))
        .build();

}