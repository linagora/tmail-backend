package com.linagora.tmail.james.jmap;

import static com.linagora.tmail.james.jmap.ContactMappingFactory.ACCOUNT_ID;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.CONTACT_ID;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.DOMAIN;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.EMAIL;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.FIRSTNAME;
import static com.linagora.tmail.james.jmap.ContactMappingFactory.SURNAME;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.backends.opensearch.DocumentId;
import org.apache.james.backends.opensearch.OpenSearchIndexer;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.opensearch.RoutingKey;
import org.apache.james.backends.opensearch.search.ScrolledSearch;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.util.FunctionalUtils;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

public class OSEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    private static final String DELIMITER = ":";
    private static final Time TIMEOUT = new Time.Builder().time("1m").build();

    private static final List<String> ALL_SEARCH_FIELDS = List.of(EMAIL, FIRSTNAME, SURNAME);
    private final OpenSearchIndexer userContactIndexer;
    private final OpenSearchIndexer domainContactIndexer;
    private final ReactorOpenSearchClient client;
    private final OpenSearchContactConfiguration configuration;
    private final ObjectMapper mapper;

    @Inject
    public OSEmailAddressContactSearchEngine(ReactorOpenSearchClient client, OpenSearchContactConfiguration contactConfiguration) {
        this.client = client;
        this.userContactIndexer = new OpenSearchIndexer(client, contactConfiguration.getUserContactWriteAliasName());
        this.domainContactIndexer = new OpenSearchIndexer(client, contactConfiguration.getDomainContactWriteAliasName());
        this.configuration = contactConfiguration;
        this.mapper = new ObjectMapper().registerModule(new GuavaModule()).registerModule(new Jdk8Module());
    }

    @Override
    public Publisher<EmailAddressContact> index(AccountId accountId, ContactFields fields) {
        EmailAddressContact emailAddressContact = EmailAddressContact.of(fields);

        SearchRequest checkDuplicatedContactOnDomainIndexRequest = new SearchRequest.Builder()
            .index(configuration.getDomainContactReadAliasName().getValue())
            .query(QueryBuilders.bool()
                .must(QueryBuilders.multiMatch().fields(EMAIL).query(fields.address().asString()).build()._toQuery())
                .should(QueryBuilders.term().field(DOMAIN).value(new FieldValue.Builder().stringValue(Username.of(accountId.getIdentifier()).getDomainPart()
                    .map(Domain::asString)
                    .orElse("")).build()).build()._toQuery())
                .minimumShouldMatch("1")
                .build()._toQuery())
            .build();

        return Throwing.supplier(() -> client.search(checkDuplicatedContactOnDomainIndexRequest)).sneakyThrow()
            .get()
            .map(searchResponse -> searchResponse.hits().total().value())
            .filter(hits -> hits == 0)
            .flatMap(any -> Mono.fromCallable(() -> mapper.writeValueAsString(new UserContactDocument(accountId, emailAddressContact)))
                .flatMap(content -> userContactIndexer.index(computeUserContactDocumentId(accountId, fields.address()), content,
                    RoutingKey.fromString(fields.address().asString()))))
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
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part, int limit) {
        SearchRequest request = new SearchRequest.Builder()
            .index(configuration.getUserContactReadAliasName().getValue(), configuration.getDomainContactReadAliasName().getValue())
            .size(limit)
            .query(buildAutoCompleteQuery(accountId, part))
            .build();

        return Throwing.supplier(() -> client.search(request)).sneakyThrow()
            .get()
            .flatMapIterable(searchResponse -> ImmutableList.copyOf(searchResponse.hits().hits()))
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow())
            .distinct(contact -> contact.fields().address());
    }

    private Query buildAutoCompleteQuery(AccountId accountId, String part) {
        Query partQuery = Optional.of(part.contains("@"))
            .filter(FunctionalUtils.identityPredicate())
            .map(mailPart -> QueryBuilders.match()
                .field((EMAIL))
                .query(new FieldValue.Builder().stringValue(part).build())
                .build()._toQuery())
            .orElse(QueryBuilders.multiMatch().fields(ALL_SEARCH_FIELDS)
                .query(part).build()._toQuery());

        return QueryBuilders.bool()
            .must(partQuery)
            .should(QueryBuilders.term().field(ACCOUNT_ID)
                .value(new FieldValue.Builder().stringValue(accountId.getIdentifier()).build())
                .build()._toQuery())
            .should(QueryBuilders.term().field(DOMAIN)
                .value(new FieldValue.Builder().stringValue(Username.of(accountId.getIdentifier()).getDomainPart()
                    .map(Domain::asString)
                    .orElse("")).build())
                .build()._toQuery())
            .minimumShouldMatch("1")
            .build()._toQuery();
    }

    @Override
    public Publisher<EmailAddressContact> list(AccountId accountId) {
        SearchRequest request = new SearchRequest.Builder()
            .index(configuration.getUserContactReadAliasName().getValue())
            .scroll(TIMEOUT)
            .query(QueryBuilders.bool()
                .should(QueryBuilders.term().field(ACCOUNT_ID).value(new FieldValue.Builder().stringValue(accountId.getIdentifier()).build()).build()._toQuery())
                .minimumShouldMatch("1")
                .build()
                ._toQuery())
            .build();

        return new ScrolledSearch(client, request)
            .searchHits()
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    @Override
    public Publisher<EmailAddressContact> list(Domain domain) {
        SearchRequest request = new SearchRequest.Builder()
            .index(configuration.getDomainContactReadAliasName().getValue())
            .scroll(TIMEOUT)
            .query(QueryBuilders.bool()
                .should(QueryBuilders.term().field(DOMAIN).value(new FieldValue.Builder().stringValue(domain.asString()).build()).build()._toQuery())
                .minimumShouldMatch("1")
                .build()
                ._toQuery())
            .build();

        return new ScrolledSearch(client, request)
            .searchHits()
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    @Override
    public Publisher<EmailAddressContact> listDomainsContacts() {
        SearchRequest request = new SearchRequest.Builder()
            .index(configuration.getDomainContactReadAliasName().getValue())
            .scroll(TIMEOUT)
            .query(QueryBuilders.matchAll().build()._toQuery())
            .build();

        return new ScrolledSearch(client, request)
            .searchHits()
            .map(Throwing.function(this::extractContentFromHit).sneakyThrow());
    }

    @Override
    public Publisher<EmailAddressContact> get(AccountId accountId, MailAddress mailAddress) {
        return Throwing.supplier(() -> client.get(new GetRequest.Builder()
                .index(configuration.getUserContactReadAliasName().getValue())
                .id(computeUserContactDocumentId(accountId, mailAddress).asString())
                .routing(mailAddress.asString())
                .build()))
            .get()
            .filter(GetResponse::found)
            .mapNotNull(GetResponse::source)
            .map(Throwing.function(this::extractContactFromSource).sneakyThrow())
            .switchIfEmpty(Mono.error(new ContactNotFoundException(mailAddress)));
    }

    @Override
    public Publisher<EmailAddressContact> get(Domain domain, MailAddress mailAddress) {
        return Throwing.supplier(() -> client.get(new GetRequest.Builder()
                .index(configuration.getDomainContactReadAliasName().getValue())
                .id(computeDomainContactDocumentId(domain, mailAddress).asString())
                .routing(mailAddress.asString())
                .build()))
            .get()
            .filter(GetResponse::found)
            .mapNotNull(GetResponse::source)
            .map(Throwing.function(this::extractContactFromSource).sneakyThrow())
            .switchIfEmpty(Mono.error(new ContactNotFoundException(mailAddress)));
    }

    private EmailAddressContact extractContactFromSource(ObjectNode source) throws AddressException {
        return new EmailAddressContact(
            UUID.fromString(source.get(CONTACT_ID).asText()),
            new ContactFields(
                new MailAddress(source.get(EMAIL).asText()),
                source.get(FIRSTNAME).asText(),
                source.get(SURNAME).asText()
            ));
    }

    private DocumentId computeUserContactDocumentId(AccountId accountId, MailAddress mailAddress) {
        return DocumentId.fromString(String.join(DELIMITER, accountId.getIdentifier(), mailAddress.asString()));
    }

    private DocumentId computeDomainContactDocumentId(Domain domain, MailAddress mailAddress) {
        return DocumentId.fromString(String.join(DELIMITER, domain.asString(), mailAddress.asString()));
    }

    private EmailAddressContact extractContentFromHit(Hit<ObjectNode> hit) throws AddressException {
        ObjectNode source = hit.source();
        return new EmailAddressContact(UUID.fromString(source.get(CONTACT_ID).asText()),
            new ContactFields(new MailAddress((source.get(EMAIL).asText().toLowerCase(Locale.US))),
                source.get(FIRSTNAME).asText(),
                source.get(SURNAME).asText()));
    }
}
