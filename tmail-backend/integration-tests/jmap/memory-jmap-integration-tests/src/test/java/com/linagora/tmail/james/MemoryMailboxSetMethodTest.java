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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.MailboxSetMethodContract;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryMailboxSetMethodTest implements MailboxSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .build();

    @Override
    public MailboxId randomMailboxId() {
        return InMemoryId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }

    @Override
    public String errorInvalidMailboxIdMessage(String value) {
        return String.format("%s is not a mailboxId: For input string: \\\"%s\\\"", value, value);
    }
}
