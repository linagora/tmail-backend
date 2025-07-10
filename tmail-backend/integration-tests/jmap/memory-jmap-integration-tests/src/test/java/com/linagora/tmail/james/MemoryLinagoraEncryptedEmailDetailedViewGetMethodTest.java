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

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.KeystoreMemoryModule;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraEncryptedEmailDetailedViewGetMethodContract;
import com.linagora.tmail.james.common.probe.JmapGuiceEncryptedEmailContentStoreProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryLinagoraEncryptedEmailDetailedViewGetMethodTest implements LinagoraEncryptedEmailDetailedViewGetMethodContract {

    @RegisterExtension
    static JamesServerExtension
        jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .mailbox(new MailboxConfiguration(true))
            .usersRepository(DEFAULT)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new InMemoryEncryptedEmailContentStoreModule())
            .overrideWith(new KeystoreMemoryModule(), new KeystoreSetMethodModule(), new KeystoreGetMethodModule(), new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule(), new InMemoryEncryptedEmailContentStoreModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(JmapGuiceEncryptedEmailContentStoreProbe.class)))
        .build();

    @Override
    public MessageId randomMessageId() {
        return InMemoryMessageId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }
}
