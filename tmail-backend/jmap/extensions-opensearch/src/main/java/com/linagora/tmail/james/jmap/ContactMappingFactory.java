package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.opensearch.IndexCreationFactory.ANALYZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.KEYWORD;
import static org.apache.james.backends.opensearch.IndexCreationFactory.PROPERTIES;
import static org.apache.james.backends.opensearch.IndexCreationFactory.SEARCH_ANALYZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.TOKENIZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.TYPE;
import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.apache.james.backends.opensearch.ElasticSearchConfiguration;
import org.opensearch.common.xcontent.XContentBuilder;

public class ContactMappingFactory {

    public static final String ACCOUNT_ID = "accountId";
    public static final String DOMAIN = "domain";
    public static final String CONTACT_ID = "contactId";
    public static final String EMAIL = "email";
    public static final String FIRSTNAME = "firstname";
    public static final String SURNAME = "surname";
    public static final String EMAIL_AUTO_COMPLETE_ANALYZER = "email_ngram_filter_analyzer";
    public static final String NAME_AUTO_COMPLETE_ANALYZER = "name_edge_ngram_filter_analyzer";
    public static final String REBUILT_KEYWORD_ANALYZER = "rebuilt_keyword";
    public static final String NGRAM_FILTER = "ngram_filter";
    public static final String EDGE_NGRAM_FILTER = "edge_ngram_filter";
    public static final String FILTER = "filter";
    public static final String STANDARD = "standard";
    public static final String TEXT = "text";
    public static final String LOWERCASE = "lowercase";
    public static final String MIN_GRAM = "min_gram";
    public static final String MAX_NGRAM = "max_gram";

    private final ElasticSearchConfiguration elasticSearchConfiguration;
    private final ElasticSearchContactConfiguration contactConfiguration;

    public ContactMappingFactory(ElasticSearchConfiguration configuration, ElasticSearchContactConfiguration contactConfiguration) {
        this.elasticSearchConfiguration = configuration;
        this.contactConfiguration = contactConfiguration;
    }

    public XContentBuilder generalContactIndicesSetting() throws IOException {
        return jsonBuilder()
            .startObject()
                .startObject("settings")
                    .field("number_of_shards", elasticSearchConfiguration.getNbShards())
                    .field("number_of_replicas", elasticSearchConfiguration.getNbReplica())
                    .field("index.write.wait_for_active_shards", elasticSearchConfiguration.getWaitForActiveShards())
                    .startObject("index")
                        .field("max_ngram_diff", contactConfiguration.getMaxNgramDiff())
                    .endObject()
                    .startObject("analysis")
                        .startObject(ANALYZER)
                            .startObject(EMAIL_AUTO_COMPLETE_ANALYZER)
                                .field(TOKENIZER, "uax_url_email")
                                .startArray(FILTER)
                                    .value(NGRAM_FILTER)
                                    .value(LOWERCASE)
                            .endArray()
                            .endObject()
                            .startObject(NAME_AUTO_COMPLETE_ANALYZER)
                                .field(TOKENIZER, STANDARD)
                                .startArray(FILTER)
                                    .value(EDGE_NGRAM_FILTER)
                                    .value(LOWERCASE)
                                .endArray()
                            .endObject()
                            .startObject(REBUILT_KEYWORD_ANALYZER)
                                .field(TOKENIZER, KEYWORD)
                                .startArray(FILTER)
                                    .value(LOWERCASE)
                                .endArray()
                            .endObject()
                        .endObject()
                        .startObject(FILTER)
                            .startObject(NGRAM_FILTER)
                                .field(TYPE, "ngram")
                                .field(MIN_GRAM, contactConfiguration.getMinNgram())
                                .field(MAX_NGRAM, contactConfiguration.getMinNgram() + contactConfiguration.getMaxNgramDiff())
                            .endObject()
                            .startObject(EDGE_NGRAM_FILTER)
                                .field(TYPE, "edge_ngram")
                                .field(MIN_GRAM, contactConfiguration.getMinNgram())
                                .field(MAX_NGRAM, contactConfiguration.getMinNgram() + contactConfiguration.getMaxNgramDiff())
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject()
            .endObject();
        }

    public XContentBuilder userContactMappingContent() throws IOException {
        return jsonBuilder()
            .startObject()
                    .startObject(PROPERTIES)
                        .startObject(ACCOUNT_ID)
                            .field(TYPE, KEYWORD)
                        .endObject()
                        .startObject(CONTACT_ID)
                            .field(TYPE, KEYWORD)
                        .endObject()
                        .startObject(EMAIL)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, EMAIL_AUTO_COMPLETE_ANALYZER)
                            .field(SEARCH_ANALYZER, REBUILT_KEYWORD_ANALYZER)
                        .endObject()
                        .startObject(FIRSTNAME)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, NAME_AUTO_COMPLETE_ANALYZER)
                            .field(SEARCH_ANALYZER, STANDARD)
                        .endObject()
                        .startObject(SURNAME)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, NAME_AUTO_COMPLETE_ANALYZER)
                            .field(SEARCH_ANALYZER, STANDARD)
                        .endObject()
                    .endObject()
            .endObject();
    }

    public XContentBuilder domainContactMappingContent() throws IOException {
        return jsonBuilder()
            .startObject()
                    .startObject(PROPERTIES)
                        .startObject(DOMAIN)
                            .field(TYPE, KEYWORD)
                        .endObject()
                        .startObject(CONTACT_ID)
                            .field(TYPE, KEYWORD)
                        .endObject()
                        .startObject(EMAIL)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, EMAIL_AUTO_COMPLETE_ANALYZER)
                            .field(SEARCH_ANALYZER, REBUILT_KEYWORD_ANALYZER)
                        .endObject()
                        .startObject(FIRSTNAME)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, NAME_AUTO_COMPLETE_ANALYZER)
                            .field(SEARCH_ANALYZER, STANDARD)
                        .endObject()
                        .startObject(SURNAME)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, NAME_AUTO_COMPLETE_ANALYZER)
                            .field(SEARCH_ANALYZER, STANDARD)
                        .endObject()
                    .endObject()
            .endObject();
    }
}
