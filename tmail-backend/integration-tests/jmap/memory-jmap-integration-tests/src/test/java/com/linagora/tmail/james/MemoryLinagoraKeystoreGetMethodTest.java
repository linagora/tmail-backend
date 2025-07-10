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

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.KeystoreMemoryModule;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraKeystoreGetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceKeystoreManagerModule;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.ForwardSetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryLinagoraKeystoreGetMethodTest implements LinagoraKeystoreGetMethodContract {
    @RegisterExtension
    static JamesServerExtension
        jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapGuiceKeystoreManagerModule())
            .overrideWith(new KeystoreMemoryModule(), new KeystoreSetMethodModule(), new KeystoreGetMethodModule(), new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule(), new InMemoryEncryptedEmailContentStoreModule()))
        .build();
}
