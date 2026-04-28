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

import java.net.URL;

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

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName()).isEqualTo("classify-email-generic");

        assertThat(result.getInlinePrompts().system()).isPresent();
        assertThat(result.getInlinePrompts().system().get().trim()).isEqualTo(ConfigurationPromptRetriever.DEFAULT_SYSTEM_PROMPT.trim());

        assertThat(result.getInlinePrompts().user()).isPresent();
        assertThat(result.getInlinePrompts().user().get()).isNotBlank();
    }

    @Test
    void fromShouldUseInlineSystemPromptWhenProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName()).isEqualTo("classify-email-generic");
        assertThat(result.getInlinePrompts().system()).contains("inline-system");
    }

    @Test
    void fromShouldUseInlineUserPromptWhenProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(USER_PROMPT_PARAM, "inline-user-template %s");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getInlinePrompts().user()).contains("inline-user-template %s");
    }

    @Test
    void fromShouldParseSystemPromptUrlAndPromptNameWhenProvided() throws Exception {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "https://example.com/prompts/latest.json");
        configuration.addProperty(PROMPT_NAME_PARAM, "my-prompt");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isPresent();
        URL url = result.getSystemPromptUrl().get();
        assertThat(url.toExternalForm()).isEqualTo("https://example.com/prompts/latest.json");
        assertThat(result.getPromptName()).isEqualTo("my-prompt");

        assertThat(result.getInlinePrompts().system()).isPresent();
        assertThat(result.getInlinePrompts().user()).isPresent();
    }

    @Test
    void fromShouldRejectWhenBothInlineSystemPromptAndUrlAreProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "https://example.com/prompts/latest.json");

        assertThatThrownBy(() -> ConfigurationPromptRetriever.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only one of " + SYSTEM_PROMPT_PARAM + " or " + SYSTEM_PROMPT_URL_PARAM);
    }

    @Test
    void fromShouldIgnoreBlankInlineSystemPromptAndFallbackToDefault() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "   ");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getInlinePrompts().system().get().trim()).contains(ConfigurationPromptRetriever.DEFAULT_SYSTEM_PROMPT.trim());
    }

    @Test
    void fromShouldIgnoreBlankInlineUserPromptAndFallbackToDefault() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(USER_PROMPT_PARAM, "   ");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getInlinePrompts().user()).isPresent();
        assertThat(result.getInlinePrompts().user().get()).isNotBlank();
    }

    @Test
    void fromShouldThrowRuntimeExceptionOnInvalidUrl() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "http://bad host");

        assertThatThrownBy(() -> ConfigurationPromptRetriever.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid prompt URL");
    }

    @Test
    void fromShouldIgnoreBlankUrl() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "   ");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
    }

    @Test
    void retrievePromptsShouldReturnInlinePromptsWhenNoUrlConfigured() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");
        configuration.addProperty(USER_PROMPT_PARAM, "inline-user");

        ConfigurationPromptRetriever retriever = ConfigurationPromptRetriever.from(configuration);

        assertThat(retriever.getInlinePrompts().user().get()).isEqualTo("inline-user");
        assertThat(retriever.getInlinePrompts().system().get()).isEqualTo("inline-system");
        assertThat(retriever.getSystemPromptUrl()).isEmpty();
    }
}
