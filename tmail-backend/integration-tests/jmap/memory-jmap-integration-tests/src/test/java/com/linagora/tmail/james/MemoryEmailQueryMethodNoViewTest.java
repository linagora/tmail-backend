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

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.JMAPConfiguration;
import org.apache.james.jmap.rfc8621.contract.EmailQueryMethodContract;
import org.apache.james.junit.categories.Unstable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryEmailQueryMethodNoViewTest implements EmailQueryMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> binder.bind(JMAPConfiguration.class)
                .toInstance(JMAPConfiguration.builder()
                    .enable()
                    .randomPort()
                    .disableEmailQueryView()
                    .build())))
        .build();

    @Test
    @Override
    @Disabled("JAMES-3377 Not supported for in-memory test")
    public void emailQueryFilterByTextShouldIgnoreMarkupsInHtmlBody(GuiceJamesServer server) {}

    @Test
    @Override
    @Disabled("JAMES-3377 Not supported for in-memory test" +
        "In memory do not attempt message parsing a performs a full match on the raw message content")
    public void emailQueryFilterByTextShouldIgnoreAttachmentContent(GuiceJamesServer server) {}

    @Override
    @Tag(Unstable.TAG)
    public void shouldListMailsReceivedBeforeADate(GuiceJamesServer server) {
        EmailQueryMethodContract.super.shouldListMailsReceivedBeforeADate(server);
    }

    @Override
    @Tag(Unstable.TAG)
    public void shouldListMailsReceivedAfterADate(GuiceJamesServer server) {
        EmailQueryMethodContract.super.shouldListMailsReceivedAfterADate(server);
    }

    @Test
    @Override
    @Disabled("JAMES-3340 Not supported for no email query view")
    public void inMailboxAfterSortedByReceivedAtShouldCollapseThreads(GuiceJamesServer server) {
    }

    @Test
    @Override
    @Disabled("JAMES-3340 Not supported for no email query view")
    public void inMailboxSortedByReceivedAtShouldCollapseThreads(GuiceJamesServer server) {
    }

    @Test
    @Override
    @Disabled("JAMES-3340 Not supported for no email query view")
    public void inMailboxSortedBySentAtShouldCollapseThreads(GuiceJamesServer server) {
    }

    @Test
    @Override
    @Disabled("JAMES-3340 Not supported for no email query view")
    public void inMailboxBeforeSortedByReceivedAtShouldCollapseThreads(GuiceJamesServer server) {
    }

}
