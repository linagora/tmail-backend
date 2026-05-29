/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.mailbox.opensearch;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.opensearch.client.opensearch._types.query_dsl.FunctionBoostMode;

public class TmailOpenSearchMailboxConfiguration {
    public static class Builder {
        private Optional<Boolean> subjectNgramEnabled;
        private Optional<Boolean> subjectNgramHeuristicEnabled;
        private Optional<Boolean> attachmentFilenameNgramEnabled;
        private Optional<Boolean> attachmentFilenameNgramHeuristicEnabled;
        private Optional<String> bodyLanguage;
        private Optional<Boolean> dateBasedDecayEnabled;
        private Optional<String> decayScale;
        private Optional<Double> decayFactor;
        private Optional<FunctionBoostMode> decayBoostMode;

        Builder() {
            subjectNgramEnabled = Optional.empty();
            subjectNgramHeuristicEnabled = Optional.empty();
            attachmentFilenameNgramEnabled = Optional.empty();
            attachmentFilenameNgramHeuristicEnabled = Optional.empty();
            bodyLanguage = Optional.empty();
            dateBasedDecayEnabled = Optional.empty();
            decayScale = Optional.empty();
            decayFactor = Optional.empty();
            decayBoostMode = Optional.empty();
        }

        public Builder subjectNgramEnabled(Boolean subjectNgramEnabled) {
            this.subjectNgramEnabled = Optional.ofNullable(subjectNgramEnabled);
            return this;
        }

        public Builder subjectNgramHeuristicEnabled(Boolean subjectNgramHeuristicEnabled) {
            this.subjectNgramHeuristicEnabled = Optional.ofNullable(subjectNgramHeuristicEnabled);
            return this;
        }

        public Builder attachmentFilenameNgramEnabled(Boolean attachmentFilenameNgramEnabled) {
            this.attachmentFilenameNgramEnabled = Optional.ofNullable(attachmentFilenameNgramEnabled);
            return this;
        }

        public Builder attachmentFilenameNgramHeuristicEnabled(Boolean attachmentFilenameNgramHeuristicEnabled) {
            this.attachmentFilenameNgramHeuristicEnabled = Optional.ofNullable(attachmentFilenameNgramHeuristicEnabled);
            return this;
        }

        public Builder bodyLanguage(String bodyLanguage) {
            this.bodyLanguage = Optional.ofNullable(bodyLanguage);
            return this;
        }

        public Builder dateBasedDecayEnabled(Boolean dateBasedDecayEnabled) {
            this.dateBasedDecayEnabled = Optional.ofNullable(dateBasedDecayEnabled);
            return this;
        }

        public Builder decayScale(String decayScale) {
            this.decayScale = Optional.ofNullable(decayScale);
            return this;
        }

        public Builder decayFactor(Double decayFactor) {
            this.decayFactor = Optional.ofNullable(decayFactor);
            return this;
        }

        public Builder decayBoostMode(FunctionBoostMode decayBoostMode) {
            this.decayBoostMode = Optional.ofNullable(decayBoostMode);
            return this;
        }

        public TmailOpenSearchMailboxConfiguration build() {
            return new TmailOpenSearchMailboxConfiguration(
                subjectNgramEnabled.orElse(DEFAULT_SUBJECT_NGRAM_DISABLED),
                subjectNgramHeuristicEnabled.orElse(DEFAULT_SUBJECT_NGRAM_HEURISTIC_DISABLED),
                attachmentFilenameNgramEnabled.orElse(DEFAULT_ATTACHMENT_FILENAME_NGRAM_DISABLED),
                attachmentFilenameNgramHeuristicEnabled.orElse(DEFAULT_ATTACHMENT_FILENAME_NGRAM_HEURISTIC_DISABLED),
                bodyLanguage,
                dateBasedDecayEnabled.orElse(DEFAULT_DATE_BASED_DECAY_DISABLED),
                decayScale.orElse(DEFAULT_DECAY_SCALE),
                decayFactor.orElse(DEFAULT_DECAY_FACTOR),
                decayBoostMode.orElse(DEFAULT_DECAY_BOOST_MODE)
            );
        }
    }

    public static Builder builder() {
        return new TmailOpenSearchMailboxConfiguration.Builder();
    }

    public static TmailOpenSearchMailboxConfiguration fromProperties(Configuration configuration) {
        return builder()
            .subjectNgramEnabled(configuration.getBoolean(SUBJECT_NGRAM_ENABLED, null))
            .subjectNgramHeuristicEnabled(configuration.getBoolean(SUBJECT_NGRAM_HEURISTIC_ENABLED, null))
            .attachmentFilenameNgramEnabled(configuration.getBoolean(ATTACHMENT_FILENAME_NGRAM_ENABLED, null))
            .attachmentFilenameNgramHeuristicEnabled(configuration.getBoolean(ATTACHMENT_FILENAME_NGRAM_HEURISTIC_ENABLED, null))
            .bodyLanguage(configuration.getString(BODY_LANGUAGE, null))
            .dateBasedDecayEnabled(configuration.getBoolean(DATE_BASED_DECAY_ENABLED, null))
            .decayScale(configuration.getString(DECAY_SCALE, null))
            .decayFactor(configuration.getDouble(DECAY_FACTOR, null))
            .decayBoostMode(Optional.ofNullable(configuration.getString(DECAY_BOOST_MODE, null))
                .map(TmailOpenSearchMailboxConfiguration::parseBoostMode)
                .orElse(null))
            .build();
    }

    private static FunctionBoostMode parseBoostMode(String value) {
        return Arrays.stream(FunctionBoostMode.values())
            .filter(m -> m.jsonValue().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown decay boost mode: '" + value + "'. Accepted values: multiply, replace, sum, avg, max, min"));
    }

    private static final String SUBJECT_NGRAM_ENABLED = "subject.ngram.enabled";
    private static final String SUBJECT_NGRAM_HEURISTIC_ENABLED = "subject.ngram.heuristic.enabled";
    private static final String ATTACHMENT_FILENAME_NGRAM_ENABLED = "attachment.filename.ngram.enabled";
    private static final String ATTACHMENT_FILENAME_NGRAM_HEURISTIC_ENABLED = "attachment.filename.ngram.heuristic.enabled";
    public static final String BODY_LANGUAGE = "opensearch.mailbox.body.language";
    public static final String DATE_BASED_DECAY_ENABLED = "opensearch.mailbox.score.date.based.decay.enabled";
    public static final String DECAY_SCALE = "opensearch.mailbox.score.date.based.decay.scale";
    public static final String DECAY_FACTOR = "opensearch.mailbox.score.date.based.decay.factor";
    public static final String DECAY_BOOST_MODE = "opensearch.mailbox.score.date.based.decay.boost.mode";

    private static final boolean DEFAULT_SUBJECT_NGRAM_DISABLED = false;
    private static final boolean DEFAULT_SUBJECT_NGRAM_HEURISTIC_DISABLED = false;
    private static final boolean DEFAULT_ATTACHMENT_FILENAME_NGRAM_DISABLED = false;
    private static final boolean DEFAULT_ATTACHMENT_FILENAME_NGRAM_HEURISTIC_DISABLED = false;
    private static final String STANDARD_ANALYZER = "standard";
    public static final boolean DEFAULT_DATE_BASED_DECAY_DISABLED = false;
    public static final String DEFAULT_DECAY_SCALE = "365d";
    public static final double DEFAULT_DECAY_FACTOR = 0.5;
    public static final FunctionBoostMode DEFAULT_DECAY_BOOST_MODE = FunctionBoostMode.Multiply;

    public static final TmailOpenSearchMailboxConfiguration DEFAULT_CONFIGURATION = builder().build();

    private final boolean subjectNgramEnabled;
    private final boolean subjectNgramHeuristicEnabled;
    private final boolean attachmentFilenameNgramEnabled;
    private final boolean attachmentFilenameNgramHeuristicEnabled;
    private final Optional<String> bodyLanguage;
    private final boolean dateBasedDecayEnabled;
    private final String decayScale;
    private final double decayFactor;
    private final FunctionBoostMode decayBoostMode;

    private TmailOpenSearchMailboxConfiguration(boolean subjectNgramEnabled, boolean subjectNgramHeuristicEnabled,
                                                boolean attachmentFilenameNgramEnabled, boolean attachmentFilenameNgramHeuristicEnabled,
                                                Optional<String> bodyLanguage, boolean dateBasedDecayEnabled,
                                                String decayScale, double decayFactor, FunctionBoostMode decayBoostMode) {
        this.subjectNgramEnabled = subjectNgramEnabled;
        this.subjectNgramHeuristicEnabled = subjectNgramHeuristicEnabled;
        this.attachmentFilenameNgramEnabled = attachmentFilenameNgramEnabled;
        this.attachmentFilenameNgramHeuristicEnabled = attachmentFilenameNgramHeuristicEnabled;
        this.bodyLanguage = bodyLanguage;
        this.dateBasedDecayEnabled = dateBasedDecayEnabled;
        this.decayScale = decayScale;
        this.decayFactor = decayFactor;
        this.decayBoostMode = decayBoostMode;
    }

    public boolean subjectNgramEnabled() {
        return subjectNgramEnabled;
    }

    public boolean subjectNgramHeuristicEnabled() {
        return subjectNgramHeuristicEnabled;
    }

    public boolean attachmentFilenameNgramEnabled() {
        return attachmentFilenameNgramEnabled;
    }

    public boolean attachmentFilenameNgramHeuristicEnabled() {
        return attachmentFilenameNgramHeuristicEnabled;
    }

    public String bodyAnalyzer() {
        return bodyLanguage.orElse(STANDARD_ANALYZER);
    }

    public boolean dateBasedDecayEnabled() {
        return dateBasedDecayEnabled;
    }

    public String decayScale() {
        return decayScale;
    }

    public double decayFactor() {
        return decayFactor;
    }

    public FunctionBoostMode decayBoostMode() {
        return decayBoostMode;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TmailOpenSearchMailboxConfiguration) {
            TmailOpenSearchMailboxConfiguration that = (TmailOpenSearchMailboxConfiguration) o;

            return Objects.equals(this.subjectNgramEnabled, that.subjectNgramEnabled)
                && Objects.equals(this.subjectNgramHeuristicEnabled, that.subjectNgramHeuristicEnabled)
                && Objects.equals(this.attachmentFilenameNgramEnabled, that.attachmentFilenameNgramEnabled)
                && Objects.equals(this.attachmentFilenameNgramHeuristicEnabled, that.attachmentFilenameNgramHeuristicEnabled)
                && Objects.equals(this.bodyLanguage, that.bodyLanguage)
                && Objects.equals(this.dateBasedDecayEnabled, that.dateBasedDecayEnabled)
                && Objects.equals(this.decayScale, that.decayScale)
                && Objects.equals(this.decayFactor, that.decayFactor)
                && Objects.equals(this.decayBoostMode, that.decayBoostMode);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(subjectNgramEnabled, subjectNgramHeuristicEnabled, attachmentFilenameNgramEnabled,
            attachmentFilenameNgramHeuristicEnabled, bodyLanguage, dateBasedDecayEnabled, decayScale, decayFactor, decayBoostMode);
    }
}
