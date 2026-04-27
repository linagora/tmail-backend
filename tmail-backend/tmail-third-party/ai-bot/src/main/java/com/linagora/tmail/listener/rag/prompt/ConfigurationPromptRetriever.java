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

import reactor.core.publisher.Mono;

public class ConfigurationPromptRetriever implements  PromptRetriever {
    private static final String SYSTEM_PROMPT_URL_PARAM = "systemPromptUrl";
    private static final String PROMPT_NAME_PARAM = "promptName";
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String USER_PROMPT_PARAM = "userPrompt";
    private static final String DEFAULT_PROMPT_NAME = "classify-email-generic";

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

    private static final String DEFAULT_USER_PROMPT = """ 
                Username (of the person receiving this mail) is %s. His/her mail address is %s.
                Below is the content of the email:
                        
                From: %s
                To: %s
                Subject: %s
                 
                Body:
                %s
                        
                ## AVAILABLE LABELS
                %s

               Classify this email and assign relevant labels.
                """;

    private final Optional<URL> systemPromptUrl;
    private final String promptName;
    private final Prompts inlinePrompts;

    public ConfigurationPromptRetriever(Prompts inlinePrompts, Optional<URL> systemPromptUrl, String promptName) {
        this.systemPromptUrl = systemPromptUrl;
        this.promptName = promptName;
        this.inlinePrompts = inlinePrompts;
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

        String userPrompt = Optional.ofNullable(configuration.getString(USER_PROMPT_PARAM, null))
            .filter(s -> !s.isBlank()).orElse(DEFAULT_USER_PROMPT);

        String inlineSystem = configuredSystemPrompt.orElse(DEFAULT_SYSTEM_PROMPT);

        String promptName = Optional.ofNullable(configuration.getString(PROMPT_NAME_PARAM, null))
            .filter(s -> !s.isBlank())
            .orElse(DEFAULT_PROMPT_NAME);

        Prompts inlinePrompts = Prompts.of(Optional.of(inlineSystem), Optional.of(userPrompt));

        return new ConfigurationPromptRetriever(inlinePrompts, systemPromptUrl, promptName);
    }

    private static Optional<URL> baseURLStringToURL(String baseUrlString) {
        try {
            return Optional.of(URI.create(baseUrlString).toURL());
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid prompt URL", e);
        }
    }

    @Override
    public Mono<Prompts> retrievePrompts() {
        if (systemPromptUrl.isEmpty()) {
            return Mono.just(inlinePrompts);
        }
        return new HttpPromptRetriever(systemPromptUrl.get(), promptName).retrievePrompts();
    }

    public Prompts getInlinePrompts() {
        return inlinePrompts;
    }

    public Optional<URL> getSystemPromptUrl() {
        return systemPromptUrl;
    }

    public String getPromptName() {
        return promptName;
    }
}
