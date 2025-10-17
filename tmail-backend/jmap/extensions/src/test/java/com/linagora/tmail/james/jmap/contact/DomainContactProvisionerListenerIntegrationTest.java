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

package com.linagora.tmail.james.jmap.contact;


import static org.apache.james.events.EventBusTestFixture.EVENT_ID;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DomainContactProvisionerListenerIntegrationTest {
    private static final MailboxSession.SessionId SESSION_ID = MailboxSession.SessionId.of(42);
    private static final Username BOB = Username.of("bob@domain.org");
    private static final Domain DOMAIN = Domain.of("domain.org");
    private static final Username ALICE = Username.of("alice@ignored.org");
    private static final Username CEDRIC = Username.of("cedric@blacklist.org");
    private static final String IGNORED_DOMAINS = "ignored.org,blacklist.org";
    private static final MailboxPath INBOX_PATH = MailboxPath.inbox(BOB);
    private static final TestId MAILBOX_ID = TestId.of(18);
    private static final MailboxAdded INBOX_ADDED = new MailboxAdded(SESSION_ID, BOB, INBOX_PATH, MAILBOX_ID, EVENT_ID);
    private static final QuotaRoot QUOTA_ROOT = QuotaRoot.quotaRoot(BOB.asString(), Optional.of(DOMAIN));
    private static final QuotaCountUsage QUOTA_COUNT = QuotaCountUsage.count(34);
    private static final QuotaSizeUsage QUOTA_SIZE = QuotaSizeUsage.size(48);
    private static final MailboxDeletion INBOX_DELETED = new MailboxDeletion(SESSION_ID, BOB, INBOX_PATH, new MailboxACL(),
        QUOTA_ROOT, QUOTA_COUNT, QUOTA_SIZE, MAILBOX_ID, EVENT_ID);

    EmailAddressContactSearchEngine contactSearchEngine;
    EventBus eventBus;

    @BeforeEach
    void setup() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("ignoredDomains", IGNORED_DOMAINS);

        this.contactSearchEngine = new InMemoryEmailAddressContactSearchEngine();
        this.eventBus = new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), RetryBackoffConfiguration.DEFAULT, new MemoryEventDeadLetters());
        eventBus.register(new DomainContactProvisionerListener(contactSearchEngine, configuration));
    }

    @Test
    void shouldIndexDomainContactWhenInboxAdded() throws AddressException {
        eventBus.dispatch(INBOX_ADDED, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
            .containsExactlyInAnyOrder(new ContactFields(new MailAddress(BOB.asString()), "", ""));
    }

    @Test
    void shouldNotIndexDomainContactWhenMailboxAddedNotInbox() {
        MailboxPath otherPath = MailboxPath.forUser(BOB, "otherMailbox");
        MailboxAdded otherMailboxAdded = new MailboxAdded(SESSION_ID, BOB, otherPath, MAILBOX_ID, EVENT_ID);
        eventBus.dispatch(otherMailboxAdded, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    void shouldNotIndexDomainContactWhenDomainIsBlacklisted() {
        MailboxAdded blacklistedMailboxAdded = new MailboxAdded(SESSION_ID, ALICE, MailboxPath.inbox(ALICE), MAILBOX_ID, EVENT_ID);
        eventBus.dispatch(blacklistedMailboxAdded, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(Domain.of("ignored.org")))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    void indexShouldBeIdempotent() throws AddressException {
        eventBus.dispatch(INBOX_ADDED, NO_KEYS).block();
        eventBus.dispatch(INBOX_ADDED, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
            .map(EmailAddressContact::fields)
            .collectList()
            .block())
            .containsExactlyInAnyOrder(new ContactFields(new MailAddress(BOB.asString()), "", ""));
    }

    @Test
    void shouldRemoveDomainContactWhenInboxDeleted() throws AddressException {
        Mono.from(contactSearchEngine.index(DOMAIN, new ContactFields(new MailAddress(BOB.asString()), "", ""))).block();

        eventBus.dispatch(INBOX_DELETED, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
            .isEmpty();
    }

    @Test
    void shouldNotRemoveDomainContactWhenMailboxDeletedNotInbox() throws AddressException {
        Mono.from(contactSearchEngine.index(DOMAIN, new ContactFields(new MailAddress(BOB.asString()), "", ""))).block();

        MailboxPath otherPath = MailboxPath.forUser(BOB, "otherMailbox");
        MailboxDeletion otherMailboxDeleted = new MailboxDeletion(SESSION_ID, BOB, otherPath, new MailboxACL(),
            QUOTA_ROOT, QUOTA_COUNT, QUOTA_SIZE, MAILBOX_ID, EVENT_ID);
        eventBus.dispatch(otherMailboxDeleted, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
                .map(EmailAddressContact::fields)
                .collectList()
                .block())
            .containsExactlyInAnyOrder(new ContactFields(new MailAddress(BOB.asString()), "", ""));
    }

    @Test
    void removeShouldBeIdempotent() throws AddressException {
        Mono.from(contactSearchEngine.index(DOMAIN, new ContactFields(new MailAddress(BOB.asString()), "", ""))).block();

        eventBus.dispatch(INBOX_DELETED, NO_KEYS).block();
        eventBus.dispatch(INBOX_DELETED, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(DOMAIN))
            .map(EmailAddressContact::fields)
            .collectList()
            .block())
            .isEmpty();
    }

    @Test
    void shouldNotRemoveDomainContactWhenDomainIsBlacklisted() throws AddressException {
        Domain blacklistedDomain = Domain.of("blacklist.org");
        ContactFields contact = new ContactFields(new MailAddress(CEDRIC.asString()), "", "");
        Mono.from(contactSearchEngine.index(blacklistedDomain, contact)).block();

        MailboxAdded blacklistedMailboxDeleted = new MailboxAdded(SESSION_ID, CEDRIC, MailboxPath.inbox(CEDRIC), MAILBOX_ID, EVENT_ID);
        eventBus.dispatch(blacklistedMailboxDeleted, NO_KEYS).block();
        assertThat(Flux.from(contactSearchEngine.list(blacklistedDomain))
            .map(EmailAddressContact::fields)
            .collectList()
            .block())
            .containsExactlyInAnyOrder(contact);
    }
}
