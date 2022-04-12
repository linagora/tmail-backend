package com.linagora.tmail.james.jmap;

import static com.linagora.tmail.james.jmap.ContactMappingFactory.ACCOUNT_ID;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.CONTACT_ID;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.DOMAIN;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.EMAIL;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.FIRSTNAME;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.SURNAME;

import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.backends.es.DocumentId;
import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.backends.es.RoutingKey;
import org.apache.james.backends.es.UpdatedRepresentation;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.dto.DomainContactDocument;
import com.linagora.tmail.james.jmap.dto.UserContactDocument;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public class ES6EmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    private static final String DELIMITER = ":";

    private final ElasticSearchIndexer userContactIndexer;
    private final ElasticSearchIndexer domainContactIndexer;
    private final ReactorElasticSearchClient client;
    private final ElasticSearchContactConfiguration configuration;
    private final ObjectMapper mapper;

    @Inject
    public ES6EmailAddressContactSearchEngine(ReactorElasticSearchClient client, ElasticSearchContactConfiguration contactConfiguration) {
        this.client = client;
        this.userContactIndexer = new ElasticSearchIndexer(client, contactConfiguration.getUserContactWriteAliasName());
        this.domainContactIndexer = new ElasticSearchIndexer(client, contactConfiguration.getDomainContactWriteAliasName());
        this.configuration = contactConfiguration;
        this.mapper = new ObjectMapper().registerModule(new GuavaModule()).registerModule(new Jdk8Module());
    }

    @Override
    public Publisher<EmailAddressContact> index(AccountId accountId, ContactFields fields) {
        EmailAddressContact emailAddressContact = EmailAddressContact.of(fields);
        return Mono.fromCallable(() -> mapper.writeValueAsString(new UserContactDocument(accountId, emailAddressContact)))
            .flatMap(content -> userContactIndexer.index(computeUserContactDocumentId(accountId, fields.address()), content,
                RoutingKey.fromString(fields.address().asString())))
            .thenReturn(emailAddressContact);
    }

    @Override
    public Publisher<EmailAddressContact> index(Domain domain, ContactFields fields) {
        EmailAddressContact emailAddressContact = EmailAddressContact.of(fields);
        return Mono.fromCallable(() -> mapper.writeValueAsString(new DomainContactDocument(domain, emailAddressContact)))
            .flatMap(content -> domainContactIndexer.index(computeDomainContactDocumentId(domain, fields.address()), content,
                RoutingKey.fromString(fields.address().asString())))
            .thenReturn(emailAddressContact);
    }

    @Override
    public Publisher<EmailAddressContact> update(AccountId accountId, MailAddress mailAddress, ContactFields updatedFields) {
        if (mailAddress.equals(updatedFields.address())) {
            EmailAddressContact updatedEmailAddressContact = EmailAddressContact.of(updatedFields);
            return Mono.fromCallable(() -> mapper.writeValueAsString(new UserContactDocument(accountId, updatedEmailAddressContact)))
                .flatMap(content -> userContactIndexer.update(
                    List.of(new UpdatedRepresentation(computeUserContactDocumentId(accountId, mailAddress), content)),
                    RoutingKey.fromString(mailAddress.asString())))
                .thenReturn(updatedEmailAddressContact);
        } else {
            return Mono.zip(Mono.from(delete(accountId, mailAddress)), Mono.from(index(accountId, updatedFields)))
                .map(Tuple2::getT2);
        }
    }

    @Override
    public Publisher<EmailAddressContact> update(Domain domain, MailAddress mailAddress, ContactFields updatedFields) {
        if (mailAddress.equals(updatedFields.address())) {
            EmailAddressContact updatedEmailAddressContact = EmailAddressContact.of(updatedFields);
            return Mono.fromCallable(() -> mapper.writeValueAsString(new DomainContactDocument(domain, updatedEmailAddressContact)))
                .flatMap(content -> domainContactIndexer.update(
                    List.of(new UpdatedRepresentation(computeDomainContactDocumentId(domain, mailAddress), content)),
                    RoutingKey.fromString(mailAddress.asString())))
                .thenReturn(updatedEmailAddressContact);
        } else {
            return Mono.zip(Mono.from(delete(domain, mailAddress)), Mono.from(index(domain, updatedFields)))
                .map(Tuple2::getT2);
        }
    }

    @Override
    public Publisher<Void> delete(AccountId accountId, MailAddress address) {
        return userContactIndexer.delete(
                List.of(computeUserContactDocumentId(accountId, address)),
                RoutingKey.fromString(address.asString()))
            .then();
    }

    @Override
    public Publisher<Void> delete(Domain domain, MailAddress address) {
        return domainContactIndexer.delete(
                List.of(computeDomainContactDocumentId(domain, address)),
                RoutingKey.fromString(address.asString()))
            .then();
    }

    @Override
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part) {
        SearchRequest request = new SearchRequest(configuration.getUserContactReadAliasName().getValue(), configuration.getDomainContactReadAliasName().getValue())
            .source(new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                    .must(QueryBuilders.multiMatchQuery(part, EMAIL, FIRSTNAME, SURNAME))
                    .should(QueryBuilders.termQuery(ACCOUNT_ID, accountId.getIdentifier()))
                    .should(QueryBuilders.termQuery(DOMAIN, Username.of(accountId.getIdentifier()).getDomainPart()
                        .map(Domain::asString)
                        .orElse("")))
                    .minimumShouldMatch(1)));

        return client.search(request, RequestOptions.DEFAULT)
            .flatMapIterable(searchResponse -> ImmutableList.copyOf(searchResponse.getHits().getHits()))
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    private DocumentId computeUserContactDocumentId(AccountId accountId, MailAddress mailAddress) {
        return DocumentId.fromString(String.join(DELIMITER, accountId.getIdentifier(), mailAddress.asString()));
    }

    private DocumentId computeDomainContactDocumentId(Domain domain, MailAddress mailAddress) {
        return DocumentId.fromString(String.join(DELIMITER, domain.asString(), mailAddress.asString()));
    }

    private EmailAddressContact extractContentFromHit(SearchHit hit) throws AddressException {
        return new EmailAddressContact(UUID.fromString((String) hit.getSourceAsMap().get(CONTACT_ID)),
            new ContactFields(new MailAddress(((String) hit.getSourceAsMap().get(EMAIL))),
                (String) hit.getSourceAsMap().get(FIRSTNAME),
                (String) hit.getSourceAsMap().get(SURNAME)));
    }
}
