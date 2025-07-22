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
package com.linagora.tmail.mailet.rag.httpclient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.linagora.tmail.mailet.rag.RagConfig;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class OpenRagHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRagHttpClient.class);
    private static final String API_KEY_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final HttpClient httpClient;
    private final RagConfig configuration;
    private final ObjectMapper objectMapper;

    public OpenRagHttpClient(RagConfig configuration) {
        this.configuration = configuration;
        this.objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        this.httpClient = buildReactorNettyHttpClient(configuration);
    }

    public Mono<Void> addDocument(Partition partition, DocumentId documentId, String textualContent, Map<String, String> metadata) {
        String url = String.format("/indexer/partition/%s/file/%s", partition.partitionName(), documentId.asString());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(metadata))
            .flatMap(metadataJson ->
                sendFormRequest(HttpMethod.POST, url, documentId, textualContent, metadataJson)
                    .onErrorResume(throwable -> {
                        if (throwable instanceof DocumentConflictException) {
                            LOGGER.info("Document already exists. Retrying with PUT.");
                            return sendFormRequest(HttpMethod.PUT, url, documentId, textualContent, metadataJson);
                        }
                        return Mono.error(throwable);
                    }))
            .onErrorResume(JsonProcessingException.class, e -> {
                LOGGER.error("Failed to serialize metadata to JSON", e);
                return Mono.error(new RuntimeException("Failed to serialize metadata to JSON", e));
            });
    }


    private Mono<Void> sendFormRequest(HttpMethod method, String url, DocumentId documentId, String textualContent, String metadataJson) {
        return httpClient
            .headers(headers -> headers.add(CONTENT_TYPE_HEADER, "multipart/form-data"))
            .request(method)
            .uri(url)
            .sendForm((req, form) -> form
                .multipart(true)
                .attr("metadata", metadataJson)
                .file("file", documentId.asString() + ".txt", new ByteArrayInputStream(textualContent.getBytes(StandardCharsets.UTF_8)), "text/plain"))
            .responseSingle((res, content) -> {
                if (res.status().code() / 100 == 2) {
                    return content.asString(StandardCharsets.UTF_8)
                        .flatMap(contentString -> {
                            return getTaskState(documentId, extractUrl(contentString));
                        });
                } else if (res.status().code() == 409 && method == HttpMethod.POST) {
                    return Mono.error(new DocumentConflictException());
                } else {
                    return content.asString(StandardCharsets.UTF_8)
                        .flatMap(body -> Mono.error(new RuntimeException("Failed to " + method.name() + " document: " + body + " (status: " + res.status().code() + ")")));
                }
            });
    }

    private @NotNull Mono<Void> getTaskState(DocumentId documentId, String extractedUrl) {
        return Mono.delay(Duration.ofSeconds(1))
            .then(httpClient
                .get()
                .uri(extractedUrl)
                    .responseSingle((res, content) ->
                        content.asString(StandardCharsets.UTF_8)
                            .flatMap(body -> Mono.fromCallable(() -> objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {}))
                                .flatMap(jsonMap -> logState(documentId, (String) jsonMap.get("task_state")))
                                .onErrorResume(JsonProcessingException.class, e -> {
                                    LOGGER.error("Failed to parse JSON content for document {}: {}", documentId.asString(), body, e);
                                    return Mono.error(new RuntimeException("Failed to parse task state JSON", e));
                                }))));
    }

    private static @NotNull Mono<Void> logState(DocumentId documentId, String taskState) {
        switch (taskState) {
            case "COMPLETED":
                LOGGER.info("Indexation Task completed successfully for document {}", documentId.asString());
                return Mono.empty();

            case "SERIALIZING":
                LOGGER.info("Indexation in progress for document {}: state={}", documentId.asString(), taskState);
                return Mono.empty();

            default:
                LOGGER.warn("Unexpected task state '{}' for document {}", taskState, documentId.asString());
                return Mono.error(new RuntimeException("Unexpected task state: " + taskState));
        }
    }

    private HttpClient buildReactorNettyHttpClient(RagConfig configuration) {
        HttpClient client = HttpClient.create()
            .baseUrl(configuration.getBaseURLOpt().get().toString())
            .headers(headers -> headers.add(API_KEY_HEADER, "Bearer " + configuration.getAuthorizationToken())
                .add("Accept", APPLICATION_JSON));

        if (configuration.getTrustAllCertificates()) {
            client = client.secure();
        }
        return client;
    }

    private String extractUrl(String content) {
        try {
            String fullUrl = objectMapper.readValue(content, new TypeReference<Map<String, String>>() {})
                .get("task_status_url");
            if (fullUrl != null) {
                URI uri = URI.create(fullUrl);
                return uri.getPath();
            }
            throw new RuntimeException("URL not found in content");
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse JSON content: {}", content, e);
            throw new RuntimeException("Failed to extract URL from content", e);
        }
    }

}
