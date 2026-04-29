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
package com.linagora.tmail.listener.rag.prompt;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.junit.jupiter.api.Test;

public class ConfigurationPromptRetrieverTest {
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String SYSTEM_PROMPT_URL_PARAM = "systemPromptUrl";
    private static final String PROMPT_NAME_PARAM = "promptName";
    private static final String USER_PROMPT_PARAM = "userPrompt";

    @Test
    void fromShouldUseDefaultInlinePromptsAndDefaultPromptNameWhenNothingConfigured() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName().value()).isEqualTo("classify-email-generic");

        assertThat(result.getInlinePrompts().system()).contains(PromptRetrieverConfiguration.DEFAULT_SYSTEM_PROMPT);

        assertThat(result.getInlinePrompts().userTemplate()).contains(PromptRetrieverConfiguration.DEFAULT_USER_PROMPT);
    }

    @Test
    void fromShouldUseInlineSystemPromptWhenProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName().value()).isEqualTo("classify-email-generic");
        assertThat(result.getInlinePrompts().system()).contains("inline-system");
    }

    @Test
    void fromShouldUseInlineUserPromptWhenProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(USER_PROMPT_PARAM, "inline-userTemplate-template %s");

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getInlinePrompts().userTemplate()).contains("inline-userTemplate-template %s");
    }

    @Test
    void fromShouldParseSystemPromptUrlAndPromptNameWhenProvided() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "https://example.com/prompts/latest.json");
        configuration.addProperty(PROMPT_NAME_PARAM, "my-prompt");

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isPresent();
        assertThat(result.getSystemPromptUrl().get()).isEqualTo("https://example.com/prompts/latest.json");
        assertThat(result.getPromptName().value()).isEqualTo("my-prompt");

        assertThat(result.getInlinePrompts().system()).contains(PromptRetrieverConfiguration.DEFAULT_SYSTEM_PROMPT);
        assertThat(result.getInlinePrompts().userTemplate()).contains(PromptRetrieverConfiguration.DEFAULT_USER_PROMPT);
    }

    @Test
    void fromShouldRejectWhenBothInlineSystemPromptAndUrlAreProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "https://example.com/prompts/latest.json");

        assertThatThrownBy(() -> PromptRetrieverConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only one of " + SYSTEM_PROMPT_PARAM + " or " + SYSTEM_PROMPT_URL_PARAM);
    }

    @Test
    void fromShouldIgnoreBlankInlineSystemPromptAndFallbackToDefault() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "   ");

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getInlinePrompts().system().trim()).contains(PromptRetrieverConfiguration.DEFAULT_SYSTEM_PROMPT.trim());
    }

    @Test
    void fromShouldIgnoreBlankInlineUserPromptAndFallbackToDefault() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(USER_PROMPT_PARAM, "   ");

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getInlinePrompts().userTemplate()).contains(PromptRetrieverConfiguration.DEFAULT_USER_PROMPT);
    }

    @Test
    void fromShouldIgnoreBlankUrl() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "   ");

        PromptRetrieverConfiguration result = PromptRetrieverConfiguration.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
    }

    @Test
    void retrievePromptsShouldReturnInlinePromptsWhenNoUrlConfigured() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");
        configuration.addProperty(USER_PROMPT_PARAM, "inline-userTemplate");

        PromptRetrieverConfiguration retriever = PromptRetrieverConfiguration.from(configuration);

        assertThat(retriever.getInlinePrompts().userTemplate()).isEqualTo("inline-userTemplate");
        assertThat(retriever.getInlinePrompts().system()).isEqualTo("inline-system");
        assertThat(retriever.getSystemPromptUrl()).isEmpty();
    }
}