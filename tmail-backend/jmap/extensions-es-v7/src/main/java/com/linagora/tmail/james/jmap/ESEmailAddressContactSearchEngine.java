package com.linagora.tmail.james.jmap;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.es.v7.DocumentId;
import org.apache.james.backends.es.v7.ElasticSearchIndexer;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.backends.es.v7.RoutingKey;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.jmap.api.model.AccountId;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.james.jmap.contact.AccountEmailContact;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

import reactor.core.publisher.Mono;

public class ESEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    private static final RoutingKey ROUTING_KEY = RoutingKey.fromString("routing");
    private static final DocumentId DOCUMENT_ID = DocumentId.fromString("1");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ElasticSearchIndexer indexer;
    private final ReactorElasticSearchClient client;
    private final ElasticSearchContactConfiguration configuration;

    public ESEmailAddressContactSearchEngine(ReactorElasticSearchClient client, ElasticSearchContactConfiguration contactConfiguration) {
        this.client = client;
        this.indexer = new ElasticSearchIndexer(client, contactConfiguration.getWriteAliasName());
        this.configuration = contactConfiguration;
    }

    @Override
    public Publisher<EmailAddressContact> index(AccountId accountId, ContactFields fields) {
        EmailAddressContact emailAddressContact = EmailAddressContact.of(fields);
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(new AccountEmailContact(accountId, emailAddressContact)))
            .flatMap(content -> indexer.index(DOCUMENT_ID, content, ROUTING_KEY))
            .thenReturn(emailAddressContact);
    }

    @Override
    public Publisher<EmailAddressContact> index(Domain domain, ContactFields fields) {
        throw new NotImplementedException("Not implemented yet!");
    }

    @Override
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part) {
        SearchRequest request = new SearchRequest(configuration.getReadAliasName().getValue());
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder matchQueryBuilder = QueryBuilders.boolQuery()
            .filter(QueryBuilders.matchQuery("accountId", accountId.getIdentifier()))
            .should(QueryBuilders.matchQuery("address", part));
        sourceBuilder.query(matchQueryBuilder);
        request.source(sourceBuilder);
        return client.search(request, RequestOptions.DEFAULT)
            .map(searchResponse -> searchResponse.getHits().getHits())
            .map(Arrays::asList)
            .flatMapIterable(Function.identity())
            .map(Throwing.function(hit -> new EmailAddressContact(UUID.fromString((String) hit.getSourceAsMap().get("id")),
                new ContactFields(new MailAddress((String) hit.getSourceAsMap().get("address")), "", ""))));
    }
}
