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

import static org.apache.james.jmap.send.MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE;

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
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

    private Optional<String> getSender(Mail mail) {
        return getSmtpAuthenticatedUser(mail)
            .or(() -> getJmapAuthenticatedUser(mail))
            .or(() -> maybeSender(mail));
    }

    private Optional<String> maybeSender(Mail mail) {
        return mail.getMaybeSender().asOptional()
            .map(MailAddress::asString);
    }

    private Optional<String> getJmapAuthenticatedUser(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, MAIL_METADATA_USERNAME_ATTRIBUTE, String.class);
    }

    private Optional<String> getSmtpAuthenticatedUser(Mail mail) {
        return AttributeUtils.getValueAndCastFromMail(mail, Mail.SMTP_AUTH_USER, String.class);
    }

    private Mono<Void> collectedContactProcess(String sender, List<MailAddress> recipients) {
        return openPaasRestClient.searchOpenPaasUserId(sender)
            .flatMapMany(openPassUserId -> Flux.fromIterable(recipients)
                .map(CardDavUtils::createObjectCreationRequest)
                .flatMap(cardDavCreationObjectRequest -> createCollectedContactIfNotExists(sender, openPassUserId, cardDavCreationObjectRequest)))
            .then();
    }

    private Mono<Void> createCollectedContactIfNotExists(String sender, String openPassUserId, CardDavCreationObjectRequest cardDavCreationObjectRequest) {
        return davClient.existsCollectedContact(sender, openPassUserId, cardDavCreationObjectRequest.uid())
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(exists -> davClient.createCollectedContact(sender, openPassUserId, cardDavCreationObjectRequest))
            .onErrorResume(error -> {
                LOGGER.error("Error while creating collected contact if not exists.", error);
                return Mono.empty();
            });
    }
}
