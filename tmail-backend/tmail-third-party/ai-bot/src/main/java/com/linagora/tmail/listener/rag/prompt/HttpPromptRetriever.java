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

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class HttpPromptRetriever implements PromptRetriever {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiPromptsBundle(List<PromptDefinition> prompts) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PromptDefinition(String name, List<MessageDefinition> messages) { }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record MessageDefinition(String role, String content) { }
    }

    public HttpPromptRetriever(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public HttpPromptRetriever() {
        this(HttpClient.create().responseTimeout(TIMEOUT), new ObjectMapper());
    }

    public Mono<String> loadPromptUrl(URL url) {
        if (url == null) {
            return Mono.error(new PromptRetrievalException("Prompt URL must not be null"));
        }
        return httpClient
            .get()
            .uri(url.toExternalForm())
            .responseSingle((response, content) -> {
                int code = response.status().code();
                if (code != 200) {
                    return Mono.error(new PromptRetrievalException("Prompt download failed (" + code + ") for " + url.toExternalForm()));
                }
                return content.asString();
            });
    }

    @Override
    public Mono<Prompts> retrievePrompts(URL url, String promptName) {
        return loadPromptUrl(url)
            .flatMap(json -> Mono.fromCallable(() -> objectMapper.readValue(json, AiPromptsBundle.class)))
            .flatMap(bundle -> Mono.justOrEmpty(extractPrompts(bundle, promptName))
                .switchIfEmpty(Mono.error(new PromptRetrievalException(
                    "Prompt '" + promptName + "' not found"))));
    }

    private Optional<Prompts> extractPrompts(AiPromptsBundle bundle, String promptName) {
        if (bundle == null || bundle.prompts() == null) {
            return Optional.empty();
        }

        return bundle.prompts().stream()
            .filter(p -> promptName.equals(p.name()))
            .findFirst()
            .map(prompt -> {
                List<AiPromptsBundle.MessageDefinition> messages = prompt.messages();
                if (messages == null) {
                    return new Prompts(Optional.empty(), Optional.empty());
                }

                Optional<String> system = messages.stream()
                    .filter(m -> "system".equalsIgnoreCase(m.role()))
                    .map(AiPromptsBundle.MessageDefinition::content)
                    .findFirst();

                Optional<String> user = messages.stream()
                    .filter(m -> "user".equalsIgnoreCase(m.role()))
                    .map(AiPromptsBundle.MessageDefinition::content)
                    .findFirst();

                return new Prompts(system, user);
            });
    }

}
