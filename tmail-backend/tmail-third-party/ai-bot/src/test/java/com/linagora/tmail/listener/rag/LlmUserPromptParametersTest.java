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
 ********************************************************************/

package com.linagora.tmail.listener.rag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmUserPromptParametersTest {

    private static final LlmMailBackendClassifierListener.LlmUserPromptParameters PARAMS =
        new LlmMailBackendClassifierListener.LlmUserPromptParameters(
            "Alice",
            "alice@example.com",
            "Please review the attached report.",
            "bob@example.com",
            "alice@example.com",
            "Q1 Report",
            "- labelId : work - Label name :Work - label description :Work-related emails");

    @Test
    void correspondingUserPromptShouldReplaceInputPlaceholderWithEmailContent() {
        String template = "INSTRUCTION:\nClassify the email.\n\nTEXT:\n{{input}}\n";

        String result = PARAMS.correspondingUserPrompt(template);

        assertThat(result).doesNotContain("{{input}}");
        assertThat(result).contains("From: bob@example.com");
        assertThat(result).contains("To: alice@example.com");
        assertThat(result).contains("Subject: Q1 Report");
        assertThat(result).contains("Please review the attached report.");
        assertThat(result).contains("labelId : work");
    }

    @Test
    void correspondingUserPromptShouldIncludeLabelsInfoWhenUsingInputPlaceholder() {
        String template = "TEXT:\n{{input}}\n";

        String result = PARAMS.correspondingUserPrompt(template);

        assertThat(result).contains("## AVAILABLE LABELS");
        assertThat(result).contains("labelId : work");
    }

    @Test
    void correspondingUserPromptShouldFallbackToStringFormattedWhenNoInputPlaceholder() {
        String template = "User: %s\nEmail: %s\nFrom: %s\nTo: %s\nSubject: %s\nBody: %s\nLabels: %s";

        String result = PARAMS.correspondingUserPrompt(template);

        assertThat(result).isEqualTo(
            "User: Alice\nEmail: alice@example.com\nFrom: bob@example.com\nTo: alice@example.com\nSubject: Q1 Report\nBody: Please review the attached report.\nLabels: - labelId : work - Label name :Work - label description :Work-related emails");
    }
}
