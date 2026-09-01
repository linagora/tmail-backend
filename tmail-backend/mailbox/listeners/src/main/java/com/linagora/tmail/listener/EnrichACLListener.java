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

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxACL.EntryKey;
import org.apache.james.mailbox.model.MailboxACL.NameType;
import org.apache.james.mailbox.model.MailboxACL.Rfc4314Rights;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Rewrites ACL entries designating a user by its sole local part (e.g. {@code alice}) into
 * fully qualified entries built with the domain of the mailbox owner (e.g. {@code alice@domain.tld}).
 *
 * Rights of a local-part-only entry are merged into the fully qualified entry when the latter already exists.
 * Negative entries are preserved, group and special entries are left untouched.
 *
 * This listener is not bound by default and must be declared in {@code listeners.xml}.
 */
public class EnrichACLListener implements EventListener.ReactiveGroupEventListener {
    public static class EnrichACLListenerGroup extends Group {

    }

    private static final Group GROUP = new EnrichACLListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(EnrichACLListener.class);
    private static final String DOMAIN_SEPARATOR = "@";

    private final MailboxManager mailboxManager;

    @Inject
    public EnrichACLListener(MailboxManager mailboxManager) {
        this.mailboxManager = mailboxManager;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        if (event instanceof MailboxACLUpdated aclUpdated) {
            return ownerDomain(aclUpdated.getMailboxPath()).isPresent()
                && hasLocalPartOnlyUserEntry(aclUpdated.getAclDiff().getNewACL());
        }
        return false;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxACLUpdated aclUpdated) {
            return ownerDomain(aclUpdated.getMailboxPath())
                .map(domain -> enrichACL(aclUpdated.getMailboxId(), aclUpdated.getMailboxPath(), domain))
                .orElseGet(Mono::empty);
        }
        return Mono.empty();
    }

    private Mono<Void> enrichACL(MailboxId mailboxId, MailboxPath mailboxPath, Domain ownerDomain) {
        MailboxSession session = mailboxManager.createSystemSession(mailboxPath.getUser());

        return Mono.from(mailboxManager.listRightsReactive(mailboxId, session))
            .filter(this::hasLocalPartOnlyUserEntry)
            .map(acl -> enrich(acl, ownerDomain))
            .flatMap(enrichedACL -> setRights(mailboxId, enrichedACL, session))
            .onErrorResume(error -> {
                LOGGER.error("Failed to enrich local-part-only ACL entries of {}", mailboxPath.asString(), error);
                return Mono.empty();
            });
    }

    private Mono<Void> setRights(MailboxId mailboxId, MailboxACL acl, MailboxSession session) {
        return Mono.<Void>fromCallable(() -> {
                mailboxManager.setRights(mailboxId, acl, session);
                return null;
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    @VisibleForTesting
    MailboxACL enrich(MailboxACL acl, Domain ownerDomain) {
        ImmutableListMultimap<EntryKey, Rfc4314Rights> rightsByEnrichedKey = acl.getEntries().entrySet().stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                entry -> enrich(entry.getKey(), ownerDomain),
                Map.Entry::getValue));

        return new MailboxACL(rightsByEnrichedKey.asMap().entrySet().stream()
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, entry -> merge(entry.getValue()))));
    }

    private EntryKey enrich(EntryKey key, Domain ownerDomain) {
        if (isLocalPartOnlyUser(key)) {
            Username enrichedUser = Username.fromLocalPartWithDomain(key.getName(), ownerDomain);
            return EntryKey.createUserEntryKey(enrichedUser, key.isNegative());
        }
        return key;
    }

    private Rfc4314Rights merge(Collection<Rfc4314Rights> rights) {
        return Rfc4314Rights.of(rights.stream()
            .flatMap(right -> right.list().stream())
            .toList());
    }

    private boolean hasLocalPartOnlyUserEntry(MailboxACL acl) {
        return acl.getEntries().keySet().stream()
            .anyMatch(this::isLocalPartOnlyUser);
    }

    private boolean isLocalPartOnlyUser(EntryKey key) {
        return key.getNameType() == NameType.user
            && !key.getName().contains(DOMAIN_SEPARATOR);
    }

    private Optional<Domain> ownerDomain(MailboxPath mailboxPath) {
        return Optional.ofNullable(mailboxPath.getUser())
            .flatMap(Username::getDomainPart);
    }
}
