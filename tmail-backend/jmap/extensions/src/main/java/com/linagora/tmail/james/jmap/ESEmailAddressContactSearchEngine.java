package com.linagora.tmail.james.jmap;

import com.linagora.tmail.james.jmap.model.EmailAddressContact;
import com.linagora.tmail.james.jmap.model.EmailAddressContactSearchEngine;
import org.apache.james.backends.es.v7.*;
import org.apache.james.jmap.api.model.AccountId;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import scala.runtime.BoxedUnit;

public class ESEmailAddressContactSearchEngine implements EmailAddressContactSearchEngine {
    private static final IndexName INDEX_NAME = new IndexName("email_contact");
    private static final WriteAliasName ALIAS_NAME = new WriteAliasName("email_contact_alias");

    private static final RoutingKey ROUTING_KEY = RoutingKey.fromString("routing");
    private static final DocumentId DOCUMENT_ID = DocumentId.fromString("1");

    private final EmailAddressContactFactory factory;
    private final ElasticSearchIndexer indexer;

    public ESEmailAddressContactSearchEngine(ReactorElasticSearchClient client) {
        this.factory = new EmailAddressContactFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION);

        this.factory.useIndex(INDEX_NAME).addAlias(ALIAS_NAME).createIndexAndAliases(client);
        this.indexer = new ElasticSearchIndexer(client, ALIAS_NAME);
    }


    @Override
    public Publisher<BoxedUnit> index(AccountId accountId, EmailAddressContact contact) {
        String content = String.format("{" +
                "\"accountId\": \"%s\"," +
                "\"id\": \"%s\", \"address\": \"%s\"" + "}", accountId.toString(), contact.id().variant(), contact.address());


        return this.indexer.index(DOCUMENT_ID, content, ROUTING_KEY).map(e -> BoxedUnit.UNIT);
    }

    @Override
    public Publisher<EmailAddressContact> autoComplete(AccountId accountId, String part) {
        Mono.fromRunnable(() -> {
            SearchRequest request = new SearchRequest("posts");
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            QueryBuilder matchQueryBuilder = QueryBuilders.boolQuery()
                    .filter(QueryBuilders.matchQuery("accountId", accountId.getIdentifier()))
                    .should(QueryBuilders.matchQuery("address", "part"));
            sourceBuilder.query(matchQueryBuilder);
            request.source(sourceBuilder);
        }).then();
    }
}
