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
 *******************************************************************/

package com.linagora.tmail.saas.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSDomainAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSAccountProvisionListenerTest {

    private static final Domain DOMAIN = Domain.of("example.com");
    private static final Username BOB = Username.of("bob@example.com");
    private static final MailboxId MAILBOX_ID = TestId.of(1);
    private static final SaaSAccount DOMAIN_ACCOUNT = new SaaSAccount(false, true);

    private MemorySaaSDomainAccountRepository domainAccountRepository;
    private MemorySaaSAccountRepository accountRepository;
    private SaaSAccountProvisionListener listener;

    @BeforeEach
    void setUp() {
        domainAccountRepository = new MemorySaaSDomainAccountRepository();
        accountRepository = new MemorySaaSAccountRepository();
        listener = new SaaSAccountProvisionListener(domainAccountRepository, accountRepository);
    }

    private MailboxEvents.MailboxAdded inboxAddedEvent(Username username) {
        return new MailboxEvents.MailboxAdded(
            null,
            username,
            MailboxPath.forUser(username, MailboxConstants.INBOX),
            MAILBOX_ID,
            Event.EventId.random());
    }

    private MailboxEvents.MailboxAdded mailboxAddedEvent(Username username, String mailboxName) {
        return new MailboxEvents.MailboxAdded(
            null,
            username,
            MailboxPath.forUser(username, mailboxName),
            MAILBOX_ID,
            Event.EventId.random());
    }

    @Test
    void shouldProvisionAccountFromDomainDefaults() {
        Mono.from(domainAccountRepository.upsertSaasDomainAccount(DOMAIN, DOMAIN_ACCOUNT)).block();

        Mono.from(listener.reactiveEvent(inboxAddedEvent(BOB))).block();

        assertThat(Mono.from(accountRepository.getSaaSAccount(BOB)).block())
            .isEqualTo(DOMAIN_ACCOUNT);
    }

    @Test
    void shouldNotProvisionWhenNoDomainDefaults() {
        Mono.from(listener.reactiveEvent(inboxAddedEvent(BOB))).block();

        assertThat(Mono.from(accountRepository.getSaaSAccount(BOB)).block())
            .isEqualTo(SaaSAccount.DEFAULT);
    }

    @Test
    void shouldIgnoreNonInboxMailboxCreation() {
        Mono.from(domainAccountRepository.upsertSaasDomainAccount(DOMAIN, DOMAIN_ACCOUNT)).block();

        Mono.from(listener.reactiveEvent(mailboxAddedEvent(BOB, "Sent"))).block();

        assertThat(Mono.from(accountRepository.getSaaSAccount(BOB)).block())
            .isEqualTo(SaaSAccount.DEFAULT);
    }

    @Test
    void isHandlingShouldReturnTrueForInboxCreation() {
        assertThat(listener.isHandling(inboxAddedEvent(BOB))).isTrue();
    }

    @Test
    void isHandlingShouldReturnFalseForNonInboxMailbox() {
        assertThat(listener.isHandling(mailboxAddedEvent(BOB, "Drafts"))).isFalse();
    }
}
