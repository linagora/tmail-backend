package com.linagora.tmail.mailet;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.util.FunctionalUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.carddav.CardDavClient;
import com.linagora.tmail.carddav.CardDavCreationFactory;
import com.linagora.tmail.carddav.CardDavCreationObjectRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CardDavCollectedContact extends GenericMailet {

    private final OpenPaasRestClient openPaasRestClient;
    private final CardDavClient cardDavClient;

    @Inject
    public CardDavCollectedContact(OpenPaasRestClient openPaasRestClient, CardDavClient cardDavClient) {
        this.openPaasRestClient = openPaasRestClient;
        this.cardDavClient = cardDavClient;
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
                .map(CardDavCreationFactory::create)
                .flatMap(cardDavCreationObjectRequest -> createCollectedContactIfNotExists(openPassUserId, cardDavCreationObjectRequest)))
            .then();
    }

    private Mono<Void> createCollectedContactIfNotExists(String openPassUserId, CardDavCreationObjectRequest cardDavCreationObjectRequest) {
        return cardDavClient.existsCollectedContact(openPassUserId, cardDavCreationObjectRequest.uid())
            .filter(FunctionalUtils.identityPredicate().negate())
            .flatMap(exists -> cardDavClient.createCollectedContact(openPassUserId, cardDavCreationObjectRequest));
    }
}
