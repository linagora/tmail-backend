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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

public class ConfigurationPromptRetrieverTest {
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String SYSTEM_PROMPT_URL_PARAM = "systemPromptUrl";
    private static final String PROMPT_NAME_PARAM = "promptName";

    @Test
    void fromShouldUseDefaultSystemPromptAndDefaultPromptNameWhenNothingConfigured() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPrompt()).isNotBlank();
        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName()).isEqualTo("classify-email");
    }

    @Test
    void fromShouldUseInlineSystemPromptWhenProvided() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "inline-system");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPrompt()).isEqualTo("inline-system");
        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName()).isEqualTo("classify-email");
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
        assertThat(result.getSystemPrompt().isEmpty());

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
    void fromShouldIgnoreBlankInlineSystemPrompt() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_PARAM, "   ");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPrompt()).isEqualTo(ConfigurationPromptRetriever.DEFAULT_SYSTEM_PROMPT);
        assertThat(result.getSystemPromptUrl()).isEmpty();
        assertThat(result.getPromptName().isEmpty());
    }

    @Test
    void fromShouldIgnoreBlankUrl() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "   ");

        ConfigurationPromptRetriever result = ConfigurationPromptRetriever.from(configuration);

        assertThat(result.getSystemPromptUrl()).isEmpty();
    }

    @Test
    void fromShouldThrowRuntimeExceptionOnInvalidUrl() {
        HierarchicalConfiguration<ImmutableNode> configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty(SYSTEM_PROMPT_URL_PARAM, "http://bad host");

        assertThatThrownBy(() -> ConfigurationPromptRetriever.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid prompt URL");
    }
}
