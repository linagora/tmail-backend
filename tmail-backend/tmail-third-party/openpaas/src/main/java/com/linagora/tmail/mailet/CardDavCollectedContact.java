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

package com.linagora.tmail.mailet;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.util.FunctionalUtils;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.dav.CardDavUtils;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.OpenPaaSUserId;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This mailet collects contacts from mail recipients and stores them in the CardDav server using the HTTP API.
 */
public class CardDavCollectedContact extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(CardDavCollectedContact.class);

    private final OpenPaasRestClient openPaasRestClient;
    private final DavClient davClient;

    @Inject
    public CardDavCollectedContact(OpenPaasRestClient openPaasRestClient, DavClient davClient) {
        this.openPaasRestClient = openPaasRestClient;
        this.davClient = davClient;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (!mail.getRecipients().isEmpty()) {
            getSender(mail)
                .ifPresent(sender -> collectedContactProcess(sender, ImmutableList.copyOf(mail.getRecipients()))
                    .block());
        }
    }

    private Optional<Username> getSender(Mail mail) {
        return getSmtpAuthenticatedUser(mail)
            .or(() -> getJmapAuthenticatedUser(mail))
            .or(() -> maybeSender(mail));
    }

    private Optional<Username> maybeSender(Mail mail) {
        return mail.getMaybeSender().asOptional()
            .map(address -> Username.of(address.asString()));
    }

    private Optional<Username> getJmapAuthenticatedUser(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, Mail.JMAP_AUTH_USER, String.class)
            .map(Username::of);
    }

    private Optional<Username> getSmtpAuthenticatedUser(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, Mail.SMTP_AUTH_USER, String.class)
            .map(Username::of);
    }

    private Mono<Void> collectedContactProcess(Username senderUsername, List<MailAddress> recipients) {
        return openPaasRestClient.searchOpenPaasUserId(senderUsername)
            .flatMapMany(openPassUserId -> Flux.fromIterable(recipients)
                .map(CardDavUtils::createObjectCreationRequest)
                .flatMap(cardDavCreationObjectRequest -> createCollectedContactIfNotExists(senderUsername, openPassUserId, cardDavCreationObjectRequest)))
            .then();
    }

    private Mono<Void> createCollectedContactIfNotExists(Username sender, OpenPaaSUserId openPassUserId, CardDavCreationObjectRequest cardDavCreationObjectRequest) {
        return davClient.carddav().existsCollectedContact(sender, openPassUserId, cardDavCreationObjectRequest.uid())
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(exists -> davClient.carddav().createCollectedContact(sender, openPassUserId, cardDavCreationObjectRequest))
            .onErrorResume(error -> {
                LOGGER.error("Error while creating collected contact if not exists.", error);
                return Mono.empty();
            });
    }
}
