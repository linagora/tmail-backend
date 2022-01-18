package com.linagora.tmail.james.jmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.james.jmap.model.AccountEmailContact;
import com.linagora.tmail.james.jmap.model.EmailAddressContact;
import com.linagora.tmail.james.jmap.model.EmailAddressContactSearchEngine;
import org.apache.james.backends.es.v7.*;
import org.apache.james.jmap.api.model.AccountId;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Function;

public class ESEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    private static final IndexName INDEX_NAME = new IndexName("email_contact");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("email_contact_alias");

    private static final RoutingKey ROUTING_KEY = RoutingKey.fromString("routing");
    private static final DocumentId DOCUMENT_ID = DocumentId.fromString("1");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final EmailAddressContactFactory factory;
    private final ElasticSearchIndexer indexer;
    private final ReactorElasticSearchClient client;

    public ESEmailAddressContactSearchEngine(ReactorElasticSearchClient client) {
        this.factory = new EmailAddressContactFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION);
        this.client = client;

        this.factory.useIndex(INDEX_NAME).addAlias(ALIAS_NAME).createIndexAndAliases(client);
        this.indexer = new ElasticSearchIndexer(client, ALIAS_NAME);
    }


    @Override
    public Publisher<Void> index(AccountId accountId, EmailAddressContact contact) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(new AccountEmailContact(accountId, contact)))
                .flatMap(content -> indexer.index(DOCUMENT_ID, content, ROUTING_KEY)).then();
    }

    @Override
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part) {
        SearchRequest request = new SearchRequest("posts");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        QueryBuilder matchQueryBuilder = QueryBuilders.boolQuery()
                .filter(QueryBuilders.matchQuery("accountId", accountId.getIdentifier()))
                .should(QueryBuilders.matchQuery("address", "part"));
        sourceBuilder.query(matchQueryBuilder);
        request.source(sourceBuilder);
        return client.search(request, RequestOptions.DEFAULT)
                .map(searchResponse -> searchResponse.getHits().getHits())
                .map(Arrays::asList)
                .flatMapIterable(Function.identity())
                .map(hit -> new EmailAddressContact(UUID.fromString((String) hit.getSourceAsMap().get("id")),
                        (String) hit.getSourceAsMap().get("address")));

    }
}
