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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

public class ConfigurationPromptRetriever {
    private static final String SYSTEM_PROMPT_URL_PARAM = "systemPromptUrl";
    private static final String PROMPT_NAME_PARAM = "promptName";
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String DEFAULT_PROMPT_NAME = "classify-email";

    public static final String DEFAULT_SYSTEM_PROMPT = """
    Analyze the email and select labels that best match its content and intent.

    Selection criteria:
    - Only assign a label when you are highly confident it applies — when in doubt, omit it
    - Choose labels whose descriptions match the email's topic, intent, or category
    - Prioritize specificity: prefer specific labels over generic ones

    OUTPUT FORMAT:
    Return label IDs as comma-separated values with no spaces.

    Examples:
    - needs-action,label_work
    - label_personal
    - needs-action
    - (empty if no labels match)

    Return ONLY the label IDs. No explanations.
    """;

    private final String systemPrompt;
    private final Optional<URL> systemPromptUrl;
    private final String promptName;

    public ConfigurationPromptRetriever(String systemPrompt, Optional<URL> systemPromptUrl, String promptName) {
        this.systemPrompt = systemPrompt;
        this.systemPromptUrl = systemPromptUrl;
        this.promptName = promptName;
    }

    public static ConfigurationPromptRetriever from(HierarchicalConfiguration<ImmutableNode>  configuration) {
        Optional<String> configuredSystemPrompt = Optional.ofNullable(configuration.getString(SYSTEM_PROMPT_PARAM, null))
            .filter(s -> !s.isBlank());

        Optional<URL> systemPromptUrl = Optional.ofNullable(configuration.getString(SYSTEM_PROMPT_URL_PARAM, null))
            .filter(s -> !s.isBlank())
            .flatMap(ConfigurationPromptRetriever::baseURLStringToURL);

        if (configuredSystemPrompt.isPresent() && systemPromptUrl.isPresent()) {
            throw new IllegalArgumentException("Only one of " + SYSTEM_PROMPT_PARAM + " or " + SYSTEM_PROMPT_URL_PARAM + " parameters should be provided, but both are present.");
        }

        String inlineSystem = configuredSystemPrompt.orElse(DEFAULT_SYSTEM_PROMPT);

        String promptName = Optional.ofNullable(configuration.getString(PROMPT_NAME_PARAM, null))
            .filter(s -> !s.isBlank())
            .orElse(DEFAULT_PROMPT_NAME);

        return new ConfigurationPromptRetriever(inlineSystem, systemPromptUrl, promptName);
    }

    private static Optional<URL> baseURLStringToURL(String baseUrlString) {
        try {
            return Optional.of(URI.create(baseUrlString).toURL());
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid prompt URL", e);
        }
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public Optional<URL> getSystemPromptUrl() {
        return systemPromptUrl;
    }

    public String getPromptName() {
        return promptName;
    }
}
