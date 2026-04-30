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

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class HttpPromptRetriever implements PromptRetriever {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .registerModule(new Jdk8Module());

    private final HttpClient httpClient;
    private final URL url;
    private final PromptName promptName;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPromptsBundle(List<PromptDefinition> prompts) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PromptDefinition(String name, List<MessageDefinition> messages) { }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MessageDefinition(String role, String content) { }
    }

    public HttpPromptRetriever(HttpClient httpClient, URL url, PromptName promptName) {
        this.httpClient = httpClient;
        this.url = url;
        this.promptName = promptName;
    }

    public HttpPromptRetriever(URL url, PromptName promptName) {
        this(HttpClient.create().responseTimeout(TIMEOUT), url, promptName);
    }

    public Mono<String> loadPromptUrl(URL url) {
        if (url == null) {
            return Mono.error(new RuntimeException("Prompt URL must not be null"));
        }
        return httpClient
            .get()
            .uri(url.toExternalForm())
            .responseSingle((response, content) -> {
                int code = response.status().code();
                if (code != 200) {
                    return Mono.error(new RuntimeException("Prompt download failed (" + code + ") for " + url.toExternalForm()));
                }
                return content.asString();
            });
    }

    @Override
    public Mono<Prompts> retrievePrompts() {
        return loadPromptUrl(url)
            .flatMap(json -> Mono.fromCallable(() -> OBJECT_MAPPER.readValue(json, AiPromptsBundle.class)))
            .flatMap(bundle -> Mono.justOrEmpty(extractPrompts(bundle, promptName))
                .switchIfEmpty(Mono.error(new RuntimeException(
                    "Prompt '" + promptName.value() + "' not found"))));
    }

    private Optional<AiPromptsBundle.PromptDefinition> findPrompt(AiPromptsBundle bundle, PromptName promptName) {
        if (bundle == null || bundle.prompts() == null) {
            return Optional.empty();
        }
        return bundle.prompts().stream()
            .filter(p -> promptName.value().equals(p.name()))
            .findFirst();
    }

    private static Optional<String> findMessageContent(List<AiPromptsBundle.MessageDefinition> messages, Role role) {
        if (messages == null) {
            return Optional.empty();
        }
        return messages.stream()
            .filter(m -> role.equalsIgnoreCase(m.role()))
            .map(AiPromptsBundle.MessageDefinition::content)
            .findFirst();
    }

    private Optional<Prompts> extractPrompts(AiPromptsBundle bundle, PromptName promptName) {

        return findPrompt(bundle, promptName).map(prompt -> {
            String system = findMessageContent(prompt.messages(), Role.system())
                .orElseThrow(() -> new RuntimeException("No system prompt found for prompt '" + promptName.value() + "'"));
            String user = findMessageContent(prompt.messages(), Role.user())
                .orElseThrow(() -> new RuntimeException("No userTemplate prompt found for prompt '" + promptName.value() + "'"));

            return new Prompts(system, user);
        });
    }
}
