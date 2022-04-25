package com.linagora.tmail.james.jmap;

import static com.linagora.tmail.james.jmap.ContactMappingFactory.ACCOUNT_ID;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.CONTACT_ID;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.DOMAIN;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.EMAIL;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.FIRSTNAME;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.SURNAME;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.internet.AddressException;

import org.apache.james.backends.es.v7.DocumentId;
import org.apache.james.backends.es.v7.ElasticSearchIndexer;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.backends.es.v7.RoutingKey;
import org.apache.james.backends.es.v7.search.ScrolledSearch;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
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
import com.linagora.tmail.james.jmap.contact.ContactNotFoundException;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.dto.DomainContactDocument;
import com.linagora.tmail.james.jmap.dto.UserContactDocument;

import reactor.core.publisher.Mono;

public class ESEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    private static final String DELIMITER = ":";
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);

    private final ElasticSearchIndexer userContactIndexer;
    private final ElasticSearchIndexer domainContactIndexer;
    private final ReactorElasticSearchClient client;
    private final ElasticSearchContactConfiguration configuration;
    private final ObjectMapper mapper;

    @Inject
    public ESEmailAddressContactSearchEngine(ReactorElasticSearchClient client, ElasticSearchContactConfiguration contactConfiguration) {
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
    public Publisher<EmailAddressContact> update(AccountId accountId, ContactFields updatedFields) {
        return index(accountId, updatedFields);
    }

    @Override
    public Publisher<EmailAddressContact> update(Domain domain, ContactFields updatedFields) {
        return index(domain, updatedFields);
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

    @Override
    public Publisher<EmailAddressContact> list(AccountId accountId) {
        SearchRequest request = new SearchRequest(configuration.getUserContactReadAliasName().getValue())
            .scroll(TIMEOUT)
            .source(new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                    .should(QueryBuilders.termQuery(ACCOUNT_ID, accountId.getIdentifier()))
                    .minimumShouldMatch(1)));

        return new ScrolledSearch(client, request)
            .searchHits()
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    @Override
    public Publisher<EmailAddressContact> list(Domain domain) {
        SearchRequest request = new SearchRequest(configuration.getDomainContactReadAliasName().getValue())
            .scroll(TIMEOUT)
            .source(new SearchSourceBuilder()
                .query(QueryBuilders.boolQuery()
                    .should(QueryBuilders.termQuery(DOMAIN, domain.asString()))
                    .minimumShouldMatch(1)));

        return new ScrolledSearch(client, request)
            .searchHits()
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    @Override
    public Publisher<EmailAddressContact> listDomainsContacts() {
        SearchRequest request = new SearchRequest(configuration.getDomainContactReadAliasName().getValue())
            .scroll(TIMEOUT)
            .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery()));

        return new ScrolledSearch(client, request)
            .searchHits()
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    @Override
    public Publisher<EmailAddressContact> get(AccountId accountId, MailAddress mailAddress) {
        return client.get(new GetRequest(configuration.getUserContactReadAliasName().getValue())
                    .id(computeUserContactDocumentId(accountId, mailAddress).asString())
                    .routing(mailAddress.asString()),
                RequestOptions.DEFAULT)
            .filter(GetResponse::isExists)
            .map(GetResponse::getSourceAsMap)
            .map(Throwing.function(this::extractContactFromSource).sneakyThrow())
            .switchIfEmpty(Mono.error(new ContactNotFoundException(mailAddress)));
    }

    @Override
    public Publisher<EmailAddressContact> get(Domain domain, MailAddress mailAddress) {
        return client.get(new GetRequest(configuration.getDomainContactReadAliasName().getValue())
                    .id(computeDomainContactDocumentId(domain, mailAddress).asString())
                    .routing(mailAddress.asString()),
                RequestOptions.DEFAULT)
            .filter(GetResponse::isExists)
            .map(GetResponse::getSourceAsMap)
            .map(Throwing.function(this::extractContactFromSource).sneakyThrow())
            .switchIfEmpty(Mono.error(new ContactNotFoundException(mailAddress)));
    }

    private EmailAddressContact extractContactFromSource(Map<String, Object> source) throws AddressException {
        return new EmailAddressContact(
            UUID.fromString((String) source.get(CONTACT_ID)),
            new ContactFields(
                new MailAddress((String) source.get(EMAIL)),
                (String) source.get(FIRSTNAME),
                (String) source.get(SURNAME)
            ));
    }

    private DocumentId computeUserContactDocumentId(AccountId accountId, MailAddress mailAddress) {
        return DocumentId.fromString(String.join(DELIMITER, accountId.getIdentifier(), mailAddress.asString()));
    }

    private DocumentId computeDomainContactDocumentId(Domain domain, MailAddress mailAddress) {
        return DocumentId.fromString(String.join(DELIMITER, domain.asString(), mailAddress.asString()));
    }

    private EmailAddressContact extractContentFromHit(SearchHit hit) throws AddressException {
        Map<String, Object> source = hit.getSourceAsMap();
        return new EmailAddressContact(UUID.fromString((String) source.get(CONTACT_ID)),
            new ContactFields(new MailAddress(((String) source.get(EMAIL))),
                (String) source.get(FIRSTNAME),
                (String) source.get(SURNAME)));
    }
}
