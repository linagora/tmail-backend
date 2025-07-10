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

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.util.Modules;
import com.linagora.tmail.encrypted.postgres.PostgresEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.postgres.PostgresKeystoreModule;
import com.linagora.tmail.james.common.LinagoraKeystoreSetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceKeystoreManagerModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;

class PostgresLinagoraKeystoreSetMethodTest implements LinagoraKeystoreSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(Modules.combine(new JmapGuiceKeystoreManagerModule(), new PostgresKeystoreModule(), new KeystoreSetMethodModule(),
            new KeystoreGetMethodModule(), new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule(),
            new PostgresEncryptedEmailContentStoreModule()))
        .build();
}