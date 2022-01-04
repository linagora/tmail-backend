package com.linagora.tmail.james.jmap;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.apache.james.backends.es.v7.AliasName;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.apache.james.backends.es.v7.IndexName;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class EmailAddressContactFactory {
    public static class AliasSpecificationStep {
        private final int nbShards;
        private final int nbReplica;
        private final int waitForActiveShards;
        private final IndexName indexName;
        private final ImmutableList.Builder<AliasName> aliases;

        AliasSpecificationStep(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName) {
            this.nbReplica = nbReplica;
            this.nbShards = nbShards;
            this.waitForActiveShards = waitForActiveShards;
            this.indexName = indexName;
            this.aliases = ImmutableList.builder();
        }

        public AliasSpecificationStep addAlias(AliasName aliasName) {
            Preconditions.checkNotNull(aliasName);
            this.aliases.add(aliasName);
            return this;
        }

        public ReactorElasticSearchClient createIndexAndAliases (ReactorElasticSearchClient c) {
            return new AutoCompletePerformer(nbShards, nbReplica, waitForActiveShards, indexName, aliases.build()).createIndexAndAliases(c, Optional.empty());
        }

        public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient c, XContentBuilder mappingContent) {
            return new AutoCompletePerformer(nbShards, nbReplica, waitForActiveShards, indexName, aliases.build()).createIndexAndAliases(c, Optional.of(mappingContent));
        }

    }

    static class AutoCompletePerformer {
        private final int nbShards;
        private final int nbReplica;
        private final int waitForActiveShards;
        private final IndexName indexName;
        private final ImmutableList<AliasName> aliases;

        public AutoCompletePerformer(int nbShards, int nbReplica, int waitForActiveShards, IndexName indexName, ImmutableList<AliasName> aliases) {
            this.aliases = aliases;
            this.indexName = indexName;
            this.nbReplica = nbReplica;
            this.waitForActiveShards = waitForActiveShards;
            this.nbShards = nbShards;
        }

        public ReactorElasticSearchClient createIndexAndAliases(ReactorElasticSearchClient client, Optional<XContentBuilder> mappingContent) {
            Preconditions.checkNotNull(indexName);
            try {
                createIndexIfNeeded(client, indexName, generateSetting(nbShards, nbReplica, waitForActiveShards), mappingContent);
                aliases.forEach(Throwing.<AliasName>consumer(alias -> createAliasIfNeeded(client, indexName, alias)).sneakyThrow());
            } catch (IOException e) {
                LOGGER.error("Error while creating index: ", e);
            }
            return client;
        }

        private void createAliasIfNeeded(ReactorElasticSearchClient client, IndexName indexName, AliasName aliasName) throws IOException {
            if(!aliasExist(client, aliasName)) {
                client.indices()
                    .updateAliases(
                        new IndicesAliasesRequest().addAliasAction(
                            new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
                                .index(indexName.getValue())
                                .alias(aliasName.getValue())),
                                       RequestOptions.DEFAULT);
            }
        }

        private boolean aliasExist(ReactorElasticSearchClient client, AliasName aliasName) throws IOException{
            return client.indices()
                    .existsAlias(new GetAliasesRequest().aliases(aliasName.getValue()), RequestOptions.DEFAULT);
        }

        private void createIndexIfNeeded(ReactorElasticSearchClient client, IndexName indexName, XContentBuilder settings, Optional<XContentBuilder> mappingContent)
        throws IOException {
            try {
                if (!indexExists(client, indexName)) {
                    CreateIndexRequest request = new CreateIndexRequest(indexName.getValue()).source(settings);
                    mappingContent.ifPresent(request::mapping);
                    client.indices().create(
                            request,
                            RequestOptions.DEFAULT);
                }
            } catch (ElasticsearchStatusException exception) {
                if (exception.getMessage().contains(INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE));
            }
        }

        private boolean indexExists(ReactorElasticSearchClient client, IndexName indexName) throws IOException {
            return client.indices().exists(new GetIndexRequest(indexName.getValue()), RequestOptions.DEFAULT);
        }

        private XContentBuilder generateSetting(int nbShards, int nbReplica, int waitForActiveShards) throws IOException {
            return jsonBuilder()
                    .startObject()
                        .startObject("settings")
                            .field("number_of_shards", nbShards)
                            .field("number_of_replicas", nbReplica)
                            .field("index.write.wait_for_active_shards", waitForActiveShards)
                            .startObject("mappings")
                                .startObject("properties")
                                    .startObject("accountId")
                                        .field("type", "text")
                                    .endObject()
                                    .startObject("id")
                                        .field("type", "text")
                                    .endObject()
                                    .startObject("address")
                                        .field("type", "text")
                                    .endObject()
                                .endObject()
                            .endObject()
                            .startObject("analysis")
                                .startObject("analyzer")
                                    .startObject("contact_analyzer")
                                        .field("tokenizer", "contact_tokenizer")
                                    .endObject()
                                .endObject()
                            .endObject()
                            .startObject("tokenizer")
                                .startObject("contact_tokenizer")
                                    .field("type", "ngram")
                                    .field("min_gram", 3)
                                    .field("max_gram", 3)
                                    .startArray("token_chars")
                                        .value("letter")
                                        .value("digit")
                                    .endArray()
                                .endObject()
                            .endObject()
                        .endObject()
                    .endObject();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoCompletePerformer.class);
    private static final String INDEX_ALREADY_EXISTS_EXCEPTION_MESSAGE = "type=resource_already_exists_exception";

    private final int nbShards;
    private final int nbReplica;
    private final int waitForActiveShards;

    @Inject
    public EmailAddressContactFactory(ElasticSearchConfiguration config) {
        this.nbShards = config.getNbShards();
        this.nbReplica = config.getNbReplica();
        this.waitForActiveShards = config.getWaitForActiveShards();
    }

    public AliasSpecificationStep useIndex(IndexName indexName) {
        Preconditions.checkNotNull(indexName);
        return new AliasSpecificationStep(nbShards, nbReplica, waitForActiveShards, indexName);
    }
}
