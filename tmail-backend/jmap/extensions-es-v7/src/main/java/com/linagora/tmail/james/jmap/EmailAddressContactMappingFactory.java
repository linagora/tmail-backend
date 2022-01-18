package com.linagora.tmail.james.jmap;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.elasticsearch.common.xcontent.XContentBuilder;

public class EmailAddressContactMappingFactory {
    public static XContentBuilder generateSetting() throws IOException {
        return jsonBuilder()
            .startObject()
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
            /*
                // TODO add the analisis / tokenizer parameter as parameters of IndexCreationFactory...
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

             */
            .endObject();
        }
}
