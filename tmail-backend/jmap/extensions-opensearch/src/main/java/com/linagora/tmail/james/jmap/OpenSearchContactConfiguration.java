package com.linagora.tmail.james.jmap;

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.opensearch.IndexName;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.WriteAliasName;

public class OpenSearchContactConfiguration {

    public static class Builder {
        private Optional<IndexName> userContactIndexName;
        private Optional<IndexName> domainContactIndexName;
        private Optional<ReadAliasName> userContactReadAliasName;
        private Optional<WriteAliasName> userContactWriteAliasName;
        private Optional<ReadAliasName> domainContactReadAliasName;
        private Optional<WriteAliasName> domainContactWriteAliasName;
        private Optional<Integer> maxNgramDiff;
        private Optional<Integer> minNgram;

        Builder() {
            userContactIndexName = Optional.empty();
            domainContactIndexName = Optional.empty();
            userContactReadAliasName = Optional.empty();
            userContactWriteAliasName = Optional.empty();
            domainContactReadAliasName = Optional.empty();
            domainContactWriteAliasName = Optional.empty();
            maxNgramDiff = Optional.empty();
            minNgram = Optional.empty();
        }

        Builder userContactIndexName(Optional<IndexName> userContactIndexName) {
            this.userContactIndexName = userContactIndexName;
            return this;
        }

        Builder domainContactIndexName(Optional<IndexName> domainContactIndexName) {
            this.domainContactIndexName = domainContactIndexName;
            return this;
        }

        Builder userContactReadAliasName(Optional<ReadAliasName> userContactReadAliasName) {
            this.userContactReadAliasName = userContactReadAliasName;
            return this;
        }

        Builder userContactWriteAliasName(Optional<WriteAliasName> userContactWriteAliasName) {
            this.userContactWriteAliasName = userContactWriteAliasName;
            return this;
        }

        Builder domainContactReadAliasName(Optional<ReadAliasName> domainContactReadAliasName) {
            this.domainContactReadAliasName = domainContactReadAliasName;
            return this;
        }

        Builder domainContactWriteAliasName(Optional<WriteAliasName> domainContactWriteAliasName) {
            this.domainContactWriteAliasName = domainContactWriteAliasName;
            return this;
        }

        Builder maxNgramDiff(Optional<Integer> maxNgramDiff) {
            this.maxNgramDiff = maxNgramDiff;
            return this;
        }

        Builder minNgram(Optional<Integer> minNgram) {
            this.minNgram = minNgram;
            return this;
        }

        public OpenSearchContactConfiguration build() {
            return new OpenSearchContactConfiguration(
                userContactIndexName.orElse(DEFAULT_INDEX_USER_CONTACT_NAME),
                domainContactIndexName.orElse(DEFAULT_INDEX_DOMAIN_CONTACT_NAME),
                userContactReadAliasName.orElse(DEFAULT_ALIAS_READ_USER_CONTACT_NAME),
                userContactWriteAliasName.orElse(DEFAULT_ALIAS_WRITE_USER_CONTACT_NAME),
                domainContactReadAliasName.orElse(DEFAULT_ALIAS_READ_DOMAIN_CONTACT_NAME),
                domainContactWriteAliasName.orElse(DEFAULT_ALIAS_WRITE_DOMAIN_CONTACT_NAME),
                maxNgramDiff.orElse(DEFAULT_MAX_NGRAM_DIFF),
                minNgram.orElse(DEFAULT_MIN_NGRAM));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static final String OPENSEARCH_INDEX_USER_CONTACT_NAME = "opensearch.index.contact.user.name";
    private static final String OPENSEARCH_INDEX_DOMAIN_CONTACT_NAME = "opensearch.index.contact.domain.name";
    private static final String OPENSEARCH_ALIAS_READ_USER_CONTACT_NAME = "opensearch.alias.read.contact.user.name";
    private static final String OPENSEARCH_ALIAS_WRITE_USER_CONTACT_NAME = "opensearch.alias.write.contact.user.name";
    private static final String OPENSEARCH_ALIAS_READ_DOMAIN_CONTACT_NAME = "opensearch.alias.read.contact.domain.name";
    private static final String OPENSEARCH_ALIAS_WRITE_DOMAIN_CONTACT_NAME = "opensearch.alias.write.contact.domain.name";
    private static final String OPENSEARCH_INDEX_CONTACT_MAX_NGRAM_DIFF = "opensearch.index.contact.max.ngram.diff";
    private static final String OPENSEARCH_INDEX_CONTACT_MIN_NGRAM = "opensearch.index.contact.min.ngram";

    public static final IndexName DEFAULT_INDEX_USER_CONTACT_NAME = new IndexName("user_contact");
    public static final IndexName DEFAULT_INDEX_DOMAIN_CONTACT_NAME = new IndexName("domain_contact");
    public static final WriteAliasName DEFAULT_ALIAS_WRITE_USER_CONTACT_NAME = new WriteAliasName("user_contact_write_alias");
    public static final ReadAliasName DEFAULT_ALIAS_READ_USER_CONTACT_NAME = new ReadAliasName("user_contact_read_alias");
    public static final WriteAliasName DEFAULT_ALIAS_WRITE_DOMAIN_CONTACT_NAME = new WriteAliasName("domain_contact_write_alias");
    public static final ReadAliasName DEFAULT_ALIAS_READ_DOMAIN_CONTACT_NAME = new ReadAliasName("domain_contact_read_alias");
    public static final Integer DEFAULT_MAX_NGRAM_DIFF = 27;
    public static final Integer DEFAULT_MIN_NGRAM = 2;

    public static final OpenSearchContactConfiguration DEFAULT_CONFIGURATION = builder().build();

    public static OpenSearchContactConfiguration fromProperties(Configuration configuration) {
        return builder()
            .userContactIndexName(computeUserContactIndexName(configuration))
            .domainContactIndexName(computeDomainContactIndexName(configuration))
            .userContactReadAliasName(computeUserContactReadAlias(configuration))
            .userContactWriteAliasName(computeUserContactWriteAlias(configuration))
            .domainContactReadAliasName(computeDomainContactReadAlias(configuration))
            .domainContactWriteAliasName(computeDomainContactWriteAlias(configuration))
            .maxNgramDiff(computeMaxNgramDiff(configuration))
            .minNgram(computeMinNgram(configuration))
            .build();
    }

    static Optional<IndexName> computeUserContactIndexName(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENSEARCH_INDEX_USER_CONTACT_NAME))
                .map(IndexName::new);
    }

    static Optional<IndexName> computeDomainContactIndexName(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENSEARCH_INDEX_DOMAIN_CONTACT_NAME))
            .map(IndexName::new);
    }

    static Optional<WriteAliasName> computeUserContactWriteAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENSEARCH_ALIAS_WRITE_USER_CONTACT_NAME))
                .map(WriteAliasName::new);
    }

    static Optional<ReadAliasName> computeUserContactReadAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENSEARCH_ALIAS_READ_USER_CONTACT_NAME))
                .map(ReadAliasName::new);
    }

    static Optional<WriteAliasName> computeDomainContactWriteAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENSEARCH_ALIAS_WRITE_DOMAIN_CONTACT_NAME))
            .map(WriteAliasName::new);
    }

    static Optional<ReadAliasName> computeDomainContactReadAlias(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(OPENSEARCH_ALIAS_READ_DOMAIN_CONTACT_NAME))
            .map(ReadAliasName::new);
    }

    static Optional<Integer> computeMaxNgramDiff(Configuration configuration) {
        return Optional.ofNullable(configuration.getInteger(OPENSEARCH_INDEX_CONTACT_MAX_NGRAM_DIFF, null));
    }

    static Optional<Integer> computeMinNgram(Configuration configuration) {
        return Optional.ofNullable(configuration.getInteger(OPENSEARCH_INDEX_CONTACT_MIN_NGRAM, null));
    }

    private final IndexName userContactIndexName;
    private final IndexName domainContactIndexName;
    private final ReadAliasName userContactReadAliasName;
    private final WriteAliasName userContactWriteAliasName;
    private final ReadAliasName domainContactReadAliasName;
    private final WriteAliasName domainContactWriteAliasName;
    private final int maxNgramDiff;
    private final int minNgram;

    private OpenSearchContactConfiguration(IndexName userContactIndexName, IndexName domainContactIndexName, ReadAliasName userContactReadAliasName,
                                           WriteAliasName userContactWriteAliasName, ReadAliasName domainContactReadAliasName, WriteAliasName domainContactWriteAliasName,
                                           int maxNgramDiff, int minNgram) {
        this.userContactIndexName = userContactIndexName;
        this.domainContactIndexName = domainContactIndexName;
        this.userContactReadAliasName = userContactReadAliasName;
        this.userContactWriteAliasName = userContactWriteAliasName;
        this.domainContactReadAliasName = domainContactReadAliasName;
        this.domainContactWriteAliasName = domainContactWriteAliasName;
        this.maxNgramDiff = maxNgramDiff;
        this.minNgram = minNgram;
    }

    public IndexName getUserContactIndexName() {
        return userContactIndexName;
    }

    public IndexName getDomainContactIndexName() {
        return domainContactIndexName;
    }

    public ReadAliasName getUserContactReadAliasName() {
        return userContactReadAliasName;
    }

    public WriteAliasName getUserContactWriteAliasName() {
        return userContactWriteAliasName;
    }

    public ReadAliasName getDomainContactReadAliasName() {
        return domainContactReadAliasName;
    }

    public WriteAliasName getDomainContactWriteAliasName() {
        return domainContactWriteAliasName;
    }

    public int getMaxNgramDiff() {
        return maxNgramDiff;
    }

    public int getMinNgram() {
        return minNgram;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof OpenSearchContactConfiguration that) {
            return Objects.equals(this.userContactIndexName, that.userContactIndexName)
                && Objects.equals(this.domainContactIndexName, that.domainContactIndexName)
                && Objects.equals(this.userContactReadAliasName, that.userContactReadAliasName)
                && Objects.equals(this.userContactWriteAliasName, that.userContactWriteAliasName)
                && Objects.equals(this.domainContactReadAliasName, that.domainContactReadAliasName)
                && Objects.equals(this.domainContactWriteAliasName, that.domainContactWriteAliasName)
                && Objects.equals(this.maxNgramDiff, that.maxNgramDiff)
                && Objects.equals(this.minNgram, that.minNgram);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(userContactIndexName, domainContactIndexName, userContactReadAliasName, userContactWriteAliasName,
            domainContactReadAliasName, domainContactWriteAliasName, maxNgramDiff, minNgram);
    }
}
