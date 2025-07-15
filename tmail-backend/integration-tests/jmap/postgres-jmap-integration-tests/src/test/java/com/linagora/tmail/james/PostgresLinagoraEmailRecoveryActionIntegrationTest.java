/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james;

import org.apache.james.JamesServerExtension;
import org.apache.james.modules.vault.TestDeleteMessageVaultPreDeletionHookModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.util.Modules;
import com.linagora.tmail.james.app.PostgresEncryptedMailboxModule;
import com.linagora.tmail.james.common.DeletedMessageVaultProbeModule;
import com.linagora.tmail.james.common.EmailRecoveryActionIntegrationTest;

public class PostgresLinagoraEmailRecoveryActionIntegrationTest implements EmailRecoveryActionIntegrationTest {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(Modules.combine(new DeletedMessageVaultProbeModule(), new TestDeleteMessageVaultPreDeletionHookModule(),
            new PostgresEncryptedMailboxModule()))
        .build();

}