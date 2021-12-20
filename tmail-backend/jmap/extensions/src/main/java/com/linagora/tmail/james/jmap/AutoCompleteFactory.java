package com.linagora.tmail.james.jmap;

import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.apache.james.backends.es.v7.IndexCreationFactory;
import org.apache.james.backends.es.v7.IndexName;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.swing.text.html.Option;
import java.io.IOException;
import java.util.Optional;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class AutoCompleteFactory {
    private void createIndexIfNeeded(ReactorElasticSearchClient client, IndexName indexName, XContentBuilder settings, Optional<XContentBuilder> mappingContent) throws IOException {
        try {
            if (!indexExists(client, indexName)) {
                CreateIndexRequest request = new CreateIndexRequest(indexName.getValue()).source(settings);
                mappingContent.ifPresent(request::mapping);
                client.indices().create(
                        request,
                        RequestOptions.DEFAULT
                );
            }
        } catch (ElasticsearchStatusException e) {
            if (e.getMessage().contains("type=resource_already_exists_exception")) {
                LoggerFactory.getLogger(AutoCompleteFactory.class).info("Index [{}] already exists", indexName.getValue());
            } else {
                throw e;
            }
        }
    }

    private boolean indexExists(ReactorElasticSearchClient client, IndexName indexName) throws IOException{
        return client.indices().exists(new GetIndexRequest(indexName.getValue()), RequestOptions.DEFAULT);
    }

    private XContentBuilder generateSetting() throws IOException {
        return jsonBuilder()
                .startObject()
                    .startObject("settings")
                        .startObject("analysis")
                            .startObject("analyzer")
                                .startObject("my_analyzer")
                                    .field("tokenizer", "my_tokenizer")
                                .endObject()
                            .endObject()
                .       endObject()
                        .startObject("tokenizer")
                            .startObject("my_tokenizer")
                            .field("type", "ngram")
                            .field("min_gram", 3)
                            .field("max_gram", 3)
                            .startArray("token_chars")
                                .value("letter")
                                .value("digit")
                            .endArray()
                        .endObject()
                    .endObject()
                .endObject();
    }
}
