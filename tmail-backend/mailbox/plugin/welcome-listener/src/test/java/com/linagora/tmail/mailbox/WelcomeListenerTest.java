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

package com.linagora.tmail.mailbox;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class WelcomeListenerTest {

    public static final Username BOB = Username.of("bob");
    private WelcomeListener testee;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        testee = new WelcomeListener(mailboxManager, FileSystemImpl.forTesting(), "classpath://file.eml");
        mailboxManager.getEventBus().register(testee);
    }

    @Test
    void shouldProvisionWelcomeMessageOnInboxCreation() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxId mailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(BOB), session)).subscribeOn(Schedulers.boundedElastic()).block();

        Long count = Flux.from(mailboxManager.getMailbox(mailboxId, session)
                .listMessagesMetadata(MessageRange.all(), session))
            .count()
            .block();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldNotProvisionWelcomeMessageOnOtherMailboxCreation() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxId mailboxId = mailboxManager.createMailbox(MailboxPath.forUser(BOB, "other"), session).get();

        Long count = Flux.from(mailboxManager.getMailbox(mailboxId, session)
                .listMessagesMetadata(MessageRange.all(), session))
            .count()
            .block();

        assertThat(count).isZero();
    }
}