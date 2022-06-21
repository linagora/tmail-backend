package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.opensearch.IndexCreationFactory.KEYWORD;

import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.opensearch.client.opensearch._types.analysis.Analyzer;
import org.opensearch.client.opensearch._types.analysis.CustomAnalyzer;
import org.opensearch.client.opensearch._types.analysis.EdgeNGramTokenFilter;
import org.opensearch.client.opensearch._types.analysis.NGramTokenFilter;
import org.opensearch.client.opensearch._types.analysis.TokenFilter;
import org.opensearch.client.opensearch._types.analysis.TokenFilterDefinition;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.opensearch.client.opensearch.indices.IndexSettingsAnalysis;

import com.google.common.collect.ImmutableMap;

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
    public static final String STANDARD = "standard";
    public static final String LOWERCASE = "lowercase";

    private final OpenSearchConfiguration openSearchConfiguration;
    private final OpenSearchContactConfiguration contactConfiguration;

    public ContactMappingFactory(OpenSearchConfiguration configuration, OpenSearchContactConfiguration contactConfiguration) {
        this.openSearchConfiguration = configuration;
        this.contactConfiguration = contactConfiguration;
    }

    public IndexSettings generalContactIndicesSetting() {
        return new IndexSettings.Builder()
            .numberOfShards(Integer.toString(openSearchConfiguration.getNbShards()))
            .numberOfReplicas(Integer.toString(openSearchConfiguration.getNbReplica()))
            .index(new IndexSettings.Builder()
                .maxNgramDiff(contactConfiguration.getMaxNgramDiff())
                .build())
            .analysis(new IndexSettingsAnalysis.Builder()
                .analyzer(new ImmutableMap.Builder<String, Analyzer>()
                    .put(EMAIL_AUTO_COMPLETE_ANALYZER, new Analyzer(new CustomAnalyzer.Builder()
                        .tokenizer("uax_url_email")
                        .filter(NGRAM_FILTER, LOWERCASE)
                        .build()))
                    .put(NAME_AUTO_COMPLETE_ANALYZER, new Analyzer(new CustomAnalyzer.Builder()
                        .tokenizer(STANDARD)
                        .filter(EDGE_NGRAM_FILTER, LOWERCASE)
                        .build()))
                    .put(REBUILT_KEYWORD_ANALYZER, new Analyzer(new CustomAnalyzer.Builder()
                        .tokenizer(KEYWORD)
                        .filter(LOWERCASE)
                        .build()))
                    .build())
                .filter(new ImmutableMap.Builder<String, TokenFilter>()
                    .put(NGRAM_FILTER, new TokenFilter.Builder()
                        .definition(new TokenFilterDefinition(new NGramTokenFilter.Builder()
                            .minGram(contactConfiguration.getMinNgram())
                            .maxGram(contactConfiguration.getMinNgram() + contactConfiguration.getMaxNgramDiff())
                            .build()))
                        .build())
                    .put(EDGE_NGRAM_FILTER, new TokenFilter.Builder()
                        .definition(new TokenFilterDefinition(new EdgeNGramTokenFilter.Builder()
                            .minGram(contactConfiguration.getMinNgram())
                            .maxGram(contactConfiguration.getMinNgram() + contactConfiguration.getMaxNgramDiff())
                            .build()))
                        .build())
                    .build())
                .build())
            .build();
        }

    public TypeMapping userContactMappingContent() {
        return new TypeMapping.Builder()
            .properties(new ImmutableMap.Builder<String, Property>()
                .put(ACCOUNT_ID, new Property(new KeywordProperty.Builder().build()))
                .put(CONTACT_ID, new Property(new KeywordProperty.Builder().build()))
                .put(EMAIL, new Property(new TextProperty.Builder().analyzer(EMAIL_AUTO_COMPLETE_ANALYZER).searchAnalyzer(REBUILT_KEYWORD_ANALYZER).build()))
                .put(FIRSTNAME, new Property(new TextProperty.Builder().analyzer(NAME_AUTO_COMPLETE_ANALYZER).searchAnalyzer(STANDARD).build()))
                .put(SURNAME, new Property(new TextProperty.Builder().analyzer(NAME_AUTO_COMPLETE_ANALYZER).searchAnalyzer(STANDARD).build()))
                .build())
            .build();
    }

    public TypeMapping domainContactMappingContent() {
        return new TypeMapping.Builder()
            .properties(new ImmutableMap.Builder<String, Property>()
                .put(DOMAIN, new Property(new KeywordProperty.Builder().build()))
                .put(CONTACT_ID, new Property(new KeywordProperty.Builder().build()))
                .put(EMAIL, new Property(new TextProperty.Builder().analyzer(EMAIL_AUTO_COMPLETE_ANALYZER).searchAnalyzer(REBUILT_KEYWORD_ANALYZER).build()))
                .put(FIRSTNAME, new Property(new TextProperty.Builder().analyzer(NAME_AUTO_COMPLETE_ANALYZER).searchAnalyzer(STANDARD).build()))
                .put(SURNAME, new Property(new TextProperty.Builder().analyzer(NAME_AUTO_COMPLETE_ANALYZER).searchAnalyzer(STANDARD).build()))
                .build())
            .build();
    }
}
