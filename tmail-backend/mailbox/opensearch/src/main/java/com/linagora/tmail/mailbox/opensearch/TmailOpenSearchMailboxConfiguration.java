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

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;

public class TmailOpenSearchMailboxConfiguration {
    public static class Builder {
        private Optional<Boolean> subjectNgramEnabled;
        private Optional<Boolean> subjectNgramHeuristicEnabled;
        private Optional<Boolean> attachmentFilenameNgramEnabled;
        private Optional<Boolean> attachmentFilenameNgramHeuristicEnabled;

        Builder() {
            subjectNgramEnabled = Optional.empty();
            subjectNgramHeuristicEnabled = Optional.empty();
            attachmentFilenameNgramEnabled = Optional.empty();
            attachmentFilenameNgramHeuristicEnabled = Optional.empty();
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

        public TmailOpenSearchMailboxConfiguration build() {
            return new TmailOpenSearchMailboxConfiguration(
                subjectNgramEnabled.orElse(DEFAULT_SUBJECT_NGRAM_DISABLED),
                subjectNgramHeuristicEnabled.orElse(DEFAULT_SUBJECT_NGRAM_HEURISTIC_DISABLED),
                attachmentFilenameNgramEnabled.orElse(DEFAULT_ATTACHMENT_FILENAME_NGRAM_DISABLED),
                attachmentFilenameNgramHeuristicEnabled.orElse(DEFAULT_ATTACHMENT_FILENAME_NGRAM_HEURISTIC_DISABLED)
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
            .build();
    }

    private static final String SUBJECT_NGRAM_ENABLED = "subject.ngram.enabled";
    private static final String SUBJECT_NGRAM_HEURISTIC_ENABLED = "subject.ngram.heuristic.enabled";
    private static final String ATTACHMENT_FILENAME_NGRAM_ENABLED = "attachment.filename.ngram.enabled";
    private static final String ATTACHMENT_FILENAME_NGRAM_HEURISTIC_ENABLED = "attachment.filename.ngram.heuristic.enabled";
    private static final boolean DEFAULT_SUBJECT_NGRAM_DISABLED = false;
    private static final boolean DEFAULT_SUBJECT_NGRAM_HEURISTIC_DISABLED = false;
    private static final boolean DEFAULT_ATTACHMENT_FILENAME_NGRAM_DISABLED = false;
    private static final boolean DEFAULT_ATTACHMENT_FILENAME_NGRAM_HEURISTIC_DISABLED = false;

    public static final TmailOpenSearchMailboxConfiguration DEFAULT_CONFIGURATION = builder().build();

    private final boolean subjectNgramEnabled;
    private final boolean subjectNgramHeuristicEnabled;
    private final boolean attachmentFilenameNgramEnabled;
    private final boolean attachmentFilenameNgramHeuristicEnabled;

    private TmailOpenSearchMailboxConfiguration(boolean subjectNgramEnabled, boolean subjectNgramHeuristicEnabled,
                                                boolean attachmentFilenameNgramEnabled, boolean attachmentFilenameNgramHeuristicEnabled) {
        this.subjectNgramEnabled = subjectNgramEnabled;
        this.subjectNgramHeuristicEnabled = subjectNgramHeuristicEnabled;
        this.attachmentFilenameNgramEnabled = attachmentFilenameNgramEnabled;
        this.attachmentFilenameNgramHeuristicEnabled = attachmentFilenameNgramHeuristicEnabled;
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

    @Override
    public final boolean equals(Object o) {
        if (o instanceof TmailOpenSearchMailboxConfiguration) {
            TmailOpenSearchMailboxConfiguration that = (TmailOpenSearchMailboxConfiguration) o;

            return Objects.equals(this.subjectNgramEnabled, that.subjectNgramEnabled)
                && Objects.equals(this.subjectNgramHeuristicEnabled, that.subjectNgramHeuristicEnabled)
                && Objects.equals(this.attachmentFilenameNgramEnabled, that.attachmentFilenameNgramEnabled)
                && Objects.equals(this.attachmentFilenameNgramHeuristicEnabled, that.attachmentFilenameNgramHeuristicEnabled);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(subjectNgramEnabled, subjectNgramHeuristicEnabled, attachmentFilenameNgramEnabled, attachmentFilenameNgramHeuristicEnabled);
    }
}
