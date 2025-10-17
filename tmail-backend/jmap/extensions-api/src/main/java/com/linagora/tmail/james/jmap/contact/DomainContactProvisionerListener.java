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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents.MailboxAdded;
import org.apache.james.mailbox.events.MailboxEvents.MailboxDeletion;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class DomainContactProvisionerListener implements EventListener.ReactiveGroupEventListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DomainContactProvisionerListener.class);
    private static final String IGNORED_DOMAINS_PROPERTY = "ignoredDomains";

    public static class DomainContactProvisionerListenerGroup extends Group {

    }

    static final Group GROUP = new DomainContactProvisionerListenerGroup();

    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final List<Domain> ignoredDomains;

    @Inject
    public DomainContactProvisionerListener(EmailAddressContactSearchEngine contactSearchEngine,
                                            HierarchicalConfiguration<ImmutableNode> configuration) {
        this.contactSearchEngine = contactSearchEngine;
        this.ignoredDomains = extractIgnoredDomains(configuration.getString(IGNORED_DOMAINS_PROPERTY, ""));
    }

    private List<Domain> extractIgnoredDomains(String domains) {
        return Arrays.stream(domains.split(","))
            .map(Domain::of)
            .toList();
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxAdded
            || event instanceof MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxAdded) {
            return handleDomainContactAdded((MailboxAdded) event);
        }
        if (event instanceof MailboxDeletion) {
            return handleDomainContactDeletion((MailboxDeletion) event);
        }
        return Mono.empty();
    }

    private Publisher<Void> handleDomainContactAdded(MailboxAdded event) {
        if (event.getMailboxPath().isInbox()) {
            Username username = event.getUsername();
            Optional<Domain> maybeDomain = username.getDomainPart();
            return maybeDomain.map(domain -> addDomainContact(domain, username))
                .orElse(Mono.empty());
        }
        return Mono.empty();
    }

    private Mono<Void> addDomainContact(Domain domain, Username username) {
        if (ignoredDomains.contains(domain)) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> new ContactFields(username.asMailAddress(), "", ""))
            .flatMap(contactFields -> Mono.from(contactSearchEngine.index(domain, contactFields)))
            .doOnError(error -> LOGGER.error("Error when indexing next contact for domain", error))
            .then();
    }

    private Publisher<Void> handleDomainContactDeletion(MailboxDeletion event) {
        if (event.getMailboxPath().isInbox()) {
            Username username = event.getUsername();
            Optional<Domain> maybeDomain = username.getDomainPart();
            return maybeDomain.map(domain -> removeDomainContact(domain, username))
                .orElse(Mono.empty());
        }
        return Mono.empty();
    }

    private Mono<Void> removeDomainContact(Domain domain, Username username) {
        if (ignoredDomains.contains(domain)) {
            return Mono.empty();
        }
        return Mono.fromCallable(username::asMailAddress)
            .flatMap(mailAddress -> Mono.from(contactSearchEngine.delete(domain, mailAddress)))
            .doOnError(error -> LOGGER.error("Error when indexing next contact for domain", error))
            .then();
    }
}
