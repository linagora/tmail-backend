/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.james.jmap;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.es.v7.IndexName;
import org.apache.james.backends.es.v7.ReadAliasName;
import org.apache.james.backends.es.v7.WriteAliasName;

public class ElasticSearchContactConfiguration {

    public static class Builder {
        private Optional<IndexName> indexName;
        private Optional<ReadAliasName> readAliasName;
        private Optional<WriteAliasName> writeAliasName;

        Builder() {
            indexName = Optional.empty();
            readAliasName = Optional.empty();
            writeAliasName = Optional.empty();
        }

        Builder indexName(Optional<IndexName> indexName) {
            this.indexName = indexName;
            return this;
        }

        Builder readAliasName(Optional<ReadAliasName> readAliasName) {
            this.readAliasName = readAliasName;
            return this;
        }

        Builder writeAliasName(Optional<WriteAliasName> writeAliasName) {
            this.writeAliasName = writeAliasName;
            return this;
        }

        public ElasticSearchContactConfiguration build() {
            return new ElasticSearchContactConfiguration(
                indexName.orElse(INDEX_NAME),
                readAliasName.orElse(ALIAS_READ_NAME),
                writeAliasName.orElse(ALIAS_WRITE_NAME));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final String ELASTICSEARCH_INDEX_NAME = "elasticsearch.index.contact.name";
    private static final String ELASTICSEARCH_ALIAS_READ_NAME = "elasticsearch.alias.read.contact.name";
    private static final String ELASTICSEARCH_ALIAS_WRITE_NAME = "elasticsearch.alias.write.contact.name";

    public static final IndexName INDEX_NAME = new IndexName("email_contact");
    public static final WriteAliasName ALIAS_WRITE_NAME = new WriteAliasName("email_contact_write_alias");
    public static final ReadAliasName ALIAS_READ_NAME = new ReadAliasName("email_contact_read_alias");

    public static final ElasticSearchContactConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static ElasticSearchContactConfiguration fromProperties(Configuration configuration) {
        return builder()
            .indexName(computeContactIndexName(configuration))
            .readAliasName(computeContactReadAlias(configuration))
            .writeAliasName(computeContactWriteAlias(configuration))
            .build();
    }

    static Optional<IndexName> computeContactIndexName(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_INDEX_NAME))
                .map(IndexName::new);
    }

    static Optional<WriteAliasName> computeContactWriteAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_WRITE_NAME))
                .map(WriteAliasName::new);
    }

    static Optional<ReadAliasName> computeContactReadAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(ELASTICSEARCH_ALIAS_READ_NAME))
                .map(ReadAliasName::new);
    }

    private final IndexName indexName;
    private final ReadAliasName readAliasName;
    private final WriteAliasName writeAliasName;

    private ElasticSearchContactConfiguration(IndexName indexName, ReadAliasName readAliasName,
                                              WriteAliasName writeAliasName) {
        this.indexName = indexName;
        this.readAliasName = readAliasName;
        this.writeAliasName = writeAliasName;
    }

    public IndexName getIndexName() {
        return indexName;
    }

    public ReadAliasName getReadAliasName() {
        return readAliasName;
    }

    public WriteAliasName getWriteAliasName() {
        return writeAliasName;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ElasticSearchContactConfiguration) {
            ElasticSearchContactConfiguration that = (ElasticSearchContactConfiguration) o;

            return Objects.equals(this.indexName, that.indexName)
                && Objects.equals(this.readAliasName, that.readAliasName)
                && Objects.equals(this.writeAliasName, that.writeAliasName);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(indexName, readAliasName, writeAliasName, writeAliasName);
    }
}
