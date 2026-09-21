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
import java.util.OptionalLong;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.util.AuditTrail;
import org.apache.james.util.FunctionalUtils;
import org.apache.james.util.ReactorUtils;
import org.apache.mailet.AttributeUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.dav.CardDavClient;
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
    private static final String AUDIT_TRAIL_PROTOCOL = "mailetcontainer";
    private static final String AUDIT_TRAIL_ACTION = "CardDavCollect";
    static final String CARDDAV_COLLECT_LIMIT_PROPERTY = "tmail.carddav.collect.limit";
    private static final OptionalLong COLLECT_LIMIT = Optional.ofNullable(System.getProperty(CARDDAV_COLLECT_LIMIT_PROPERTY))
        .stream()
        .mapToLong(Long::parseLong)
        .filter(l -> l > 0)
        .findFirst();

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
                .ifPresent(sender -> collectedContactProcess(sender, ImmutableList.copyOf(mail.getRecipients()), mail)
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

    private Mono<Void> collectedContactProcess(Username senderUsername, List<MailAddress> recipients, Mail mail) {
        return openPaasRestClient.searchOpenPaasUserId(senderUsername)
            .flatMapMany(openPassUserId -> applyCollectLimit(Flux.fromIterable(recipients))
                .map(CardDavUtils::createObjectCreationRequest)
                .flatMap(cardDavCreationObjectRequest -> createCollectedContactIfNotExists(senderUsername, openPassUserId, cardDavCreationObjectRequest, mail), ReactorUtils.LOW_CONCURRENCY))
            .then();
    }

    private <T> Flux<T> applyCollectLimit(Flux<T> flux) {
        return COLLECT_LIMIT.isPresent() ? flux.take(COLLECT_LIMIT.getAsLong()) : flux;
    }


    private Mono<Void> createCollectedContactIfNotExists(Username sender, OpenPaaSUserId openPassUserId, CardDavCreationObjectRequest cardDavCreationObjectRequest, Mail mail) {
        CardDavClient cardDavClient = davClient.carddav(sender);
        return cardDavClient.existsCollectedContact(openPassUserId, cardDavCreationObjectRequest.uid())
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(exists -> cardDavClient.createCollectedContact(openPassUserId, cardDavCreationObjectRequest)
                .then(auditCollectedContact(mail, sender, cardDavCreationObjectRequest)))
            .onErrorResume(error -> {
                LOGGER.error("Error while creating collected contact if not exists.", error);
                return Mono.empty();
            });
    }

    private Mono<Void> auditCollectedContact(Mail mail, Username sender, CardDavCreationObjectRequest cardDavCreationObjectRequest) {
        return ReactorUtils.logAsMono(() -> AuditTrail.entry()
            .protocol(AUDIT_TRAIL_PROTOCOL)
            .action(AUDIT_TRAIL_ACTION)
            .username(sender::asString)
            .parameters(Throwing.supplier(() -> ImmutableMap.of(
                "mailId", mail.getName(),
                "mimeMessageId", Optional.ofNullable(mail.getMessage())
                    .map(Throwing.function(MimeMessage::getMessageID))
                    .orElse(""),
                "sender", sender.asString(),
                "contact", cardDavCreationObjectRequest.email().value().asString(),
                "contactUid", cardDavCreationObjectRequest.uid().value())))
            .log("Collected a contact."));
    }
}
