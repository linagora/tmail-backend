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

package com.linagora.tmail.listener;

import static org.apache.james.mailbox.model.MailboxACL.NEGATIVE_KEY;
import static org.apache.james.mailbox.model.MailboxACL.Right.Lookup;
import static org.apache.james.mailbox.model.MailboxACL.Right.Read;
import static org.apache.james.mailbox.model.MailboxACL.Right.Write;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.Entry;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

class EnrichACLListenerTest {
    private static class CountingACLUpdatedListener implements EventListener.ReactiveGroupEventListener {
        private static class CountingACLUpdatedListenerGroup extends Group {

        }

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Group getDefaultGroup() {
            return new CountingACLUpdatedListenerGroup();
        }

        @Override
        public boolean isHandling(Event event) {
            return event instanceof MailboxACLUpdated;
        }

        @Override
        public Publisher<Void> reactiveEvent(Event event) {
            return Mono.fromRunnable(count::incrementAndGet);
        }
    }

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username BOB_WITHOUT_DOMAIN = Username.of("bob");
    private static final EntryKey ALICE_LOCAL_PART = EntryKey.createUserEntryKey("alice");
    private static final EntryKey ALICE = EntryKey.createUserEntryKey("alice@domain.tld");
    private static final EntryKey DAVID = EntryKey.createUserEntryKey("david@domain.tld");
    private static final EntryKey SALES_GROUP = EntryKey.createGroupEntryKey("sales@domain.tld");

    private InMemoryMailboxManager mailboxManager;
    private EventBus eventBus;
    private MailboxSession bobSession;
    private MailboxId bobInboxId;
    private CountingACLUpdatedListener countingListener;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        eventBus = resources.getEventBus();
        bobSession = mailboxManager.createSystemSession(BOB);
        bobInboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), bobSession).orElseThrow();
        countingListener = new CountingACLUpdatedListener();
        eventBus.register(countingListener);
        eventBus.register(new EnrichACLListener(mailboxManager));
    }

    @Test
    void shouldAppendOwnerDomainToLocalPartOnlyUserEntries() throws Exception {
        seedACL(bobInboxId, bobSession, new MailboxACL(new Entry(ALICE_LOCAL_PART, new Rfc4314Rights(Lookup, Read))));

        assertThat(mailboxManager.listRights(bobInboxId, bobSession).getEntries())
            .containsOnly(Map.entry(ALICE, new Rfc4314Rights(Lookup, Read)));
    }

    @Test
    void shouldPreserveNegativeEntries() throws Exception {
        EntryKey negativeAliceLocalPart = EntryKey.createUserEntryKey("alice", NEGATIVE_KEY);
        EntryKey negativeAlice = EntryKey.createUserEntryKey("alice@domain.tld", NEGATIVE_KEY);

        seedACL(bobInboxId, bobSession, new MailboxACL(new Entry(negativeAliceLocalPart, new Rfc4314Rights(Write))));

        assertThat(mailboxManager.listRights(bobInboxId, bobSession).getEntries())
            .containsOnly(Map.entry(negativeAlice, new Rfc4314Rights(Write)));
    }

    @Test
    void shouldMergeRightsWhenFullyQualifiedEntryAlreadyExists() throws Exception {
        seedACL(bobInboxId, bobSession, new MailboxACL(
            new Entry(ALICE_LOCAL_PART, new Rfc4314Rights(Lookup, Read)),
            new Entry(ALICE, new Rfc4314Rights(Write))));

        assertThat(mailboxManager.listRights(bobInboxId, bobSession).getEntries())
            .containsOnly(Map.entry(ALICE, new Rfc4314Rights(Lookup, Read, Write)));
    }

    @Test
    void shouldLeaveFullyQualifiedGroupAndSpecialEntriesUntouched() throws Exception {
        seedACL(bobInboxId, bobSession, new MailboxACL(
            new Entry(ALICE_LOCAL_PART, new Rfc4314Rights(Lookup)),
            new Entry(DAVID, new Rfc4314Rights(Read)),
            new Entry(SALES_GROUP, new Rfc4314Rights(Write)),
            new Entry(MailboxACL.ANYONE_KEY, new Rfc4314Rights(Lookup))));

        assertThat(mailboxManager.listRights(bobInboxId, bobSession).getEntries())
            .containsOnly(
                Map.entry(ALICE, new Rfc4314Rights(Lookup)),
                Map.entry(DAVID, new Rfc4314Rights(Read)),
                Map.entry(SALES_GROUP, new Rfc4314Rights(Write)),
                Map.entry(MailboxACL.ANYONE_KEY, new Rfc4314Rights(Lookup)));
    }

    @Test
    void shouldNotRewriteACLWhenNoLocalPartOnlyEntry() throws Exception {
        seedACL(bobInboxId, bobSession, new MailboxACL(new Entry(ALICE, new Rfc4314Rights(Lookup, Read))));

        assertThat(mailboxManager.listRights(bobInboxId, bobSession).getEntries())
            .containsOnly(Map.entry(ALICE, new Rfc4314Rights(Lookup, Read)));
        assertThat(countingListener.count.get()).isEqualTo(1);
    }

    @Test
    void shouldRewriteACLOnlyOnce() throws Exception {
        seedACL(bobInboxId, bobSession, new MailboxACL(new Entry(ALICE_LOCAL_PART, new Rfc4314Rights(Lookup, Read))));

        assertThat(countingListener.count.get()).isEqualTo(2);
    }

    @Test
    void shouldIgnoreMailboxesWhoseOwnerHasNoDomain() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB_WITHOUT_DOMAIN);
        MailboxId inboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB_WITHOUT_DOMAIN), session).orElseThrow();

        seedACL(inboxId, session, new MailboxACL(new Entry(ALICE_LOCAL_PART, new Rfc4314Rights(Lookup, Read))));

        assertThat(mailboxManager.listRights(inboxId, session).getEntries())
            .containsOnly(Map.entry(ALICE_LOCAL_PART, new Rfc4314Rights(Lookup, Read)));
    }

    /**
     * Local-part-only ACL entries are refused by the mailbox manager when cross-domain sharing is disabled:
     * such legacy entries are seeded straight into the storage and the corresponding event is dispatched by hand.
     */
    private void seedACL(MailboxId mailboxId, MailboxSession session, MailboxACL acl) {
        MailboxMapper mapper = mailboxManager.getMapperFactory().getMailboxMapper(session);
        Mailbox mailbox = mapper.findMailboxById(mailboxId).block();

        mapper.setACL(mailbox, acl)
            .flatMap(aclDiff -> eventBus.dispatch(EventFactory.aclUpdated()
                    .randomEventId()
                    .mailboxSession(session)
                    .mailbox(mailbox)
                    .aclDiff(aclDiff)
                    .build(),
                new MailboxIdRegistrationKey(mailboxId)))
            .block();
    }
}
