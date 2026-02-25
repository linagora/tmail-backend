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

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.MailboxConstants;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class SaaSAccountProvisionListener implements EventListener.ReactiveGroupEventListener {

    public static class SaaSAccountProvisionListenerGroup extends Group {

    }

    private static final SaaSAccountProvisionListenerGroup GROUP = new SaaSAccountProvisionListenerGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(SaaSAccountProvisionListener.class);

    private final SaaSDomainAccountRepository saaSDomainAccountRepository;
    private final SaaSAccountRepository saaSAccountRepository;

    @Inject
    public SaaSAccountProvisionListener(SaaSDomainAccountRepository saaSDomainAccountRepository,
                                        SaaSAccountRepository saaSAccountRepository) {
        this.saaSDomainAccountRepository = saaSDomainAccountRepository;
        this.saaSAccountRepository = saaSAccountRepository;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        if (event instanceof MailboxEvents.MailboxAdded mailboxAdded) {
            return mailboxAdded.getMailboxPath().getName().equalsIgnoreCase(MailboxConstants.INBOX);
        }
        return false;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (!isHandling(event)) {
            return Mono.empty();
        }

        Username username = event.getUsername();
        return username.getDomainPart()
            .map(domain -> provisionAccountFromDomainDefaults(username, domain))
            .orElse(Mono.empty());
    }

    private Mono<Void> provisionAccountFromDomainDefaults(Username username, Domain domain) {
        return Mono.from(saaSDomainAccountRepository.getSaaSDomainAccount(domain))
            .flatMap(domainAccount -> {
                LOGGER.info("Provisioning SaaS account for new user {} from domain {} defaults: canUpgrade={}, isPaying={}",
                    username.asString(), domain.asString(), domainAccount.canUpgrade(), domainAccount.isPaying());
                return Mono.from(saaSAccountRepository.upsertSaasAccount(username, domainAccount));
            })
            .onErrorResume(e -> {
                LOGGER.error("Error provisioning SaaS account for user {}", username.asString(), e);
                return Mono.empty();
            });
    }
}
