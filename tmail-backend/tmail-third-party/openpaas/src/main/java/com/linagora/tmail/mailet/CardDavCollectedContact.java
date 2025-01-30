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

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.util.FunctionalUtils;
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
            mail.getMaybeSender().asOptional()
                .ifPresent(sender -> collectedContactProcess(sender, ImmutableList.copyOf(mail.getRecipients()))
                    .block());
        }
    }

    private Mono<Void> collectedContactProcess(MailAddress sender, List<MailAddress> recipients) {
        return openPaasRestClient.searchOpenPaasUserId(sender.asString())
            .flatMapMany(openPassUserId -> Flux.fromIterable(recipients)
                .map(CardDavUtils::createObjectCreationRequest)
                .flatMap(cardDavCreationObjectRequest -> createCollectedContactIfNotExists(sender, openPassUserId, cardDavCreationObjectRequest)))
            .then();
    }

    private Mono<Void> createCollectedContactIfNotExists(MailAddress sender, String openPassUserId, CardDavCreationObjectRequest cardDavCreationObjectRequest) {
        return davClient.existsCollectedContact(sender.asString(), openPassUserId, cardDavCreationObjectRequest.uid())
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(exists -> davClient.createCollectedContact(sender.asString(), openPassUserId, cardDavCreationObjectRequest))
            .onErrorResume(error -> {
                LOGGER.error("Error while creating collected contact if not exists.", error);
                return Mono.empty();
            });
    }
}
