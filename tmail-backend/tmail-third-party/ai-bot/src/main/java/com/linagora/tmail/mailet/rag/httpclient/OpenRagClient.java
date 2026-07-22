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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.linagora.tmail.mailet.conf.AiHttpClientConfiguration;
import com.linagora.tmail.mailet.rag.RagConfig;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class OpenRagClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRagClient.class);
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String RAG_DOCUMENT_ENDPOINT = "/indexer/partition/%s/file/%s";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Inject
    public OpenRagClient(RagConfig configuration) {
        this.objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        this.httpClient = AiHttpClientFactory.create(AiHttpClientConfiguration.from(configuration));
    }

    public Mono<String> addDocument(Partition partition, DocumentId documentId, String textualContent, Map<String, String> metadata) {
        String url = String.format(RAG_DOCUMENT_ENDPOINT, partition.partitionName(), documentId.asString());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(metadata))
            .flatMap(metadataJson ->
                sendFormRequest(HttpMethod.POST, url, documentId, textualContent, metadataJson)
                    .onErrorResume(DocumentConflictException.class, throwable -> {
                        LOGGER.debug("Document already exists. Retrying with PUT.");
                        return sendFormRequest(HttpMethod.PUT, url, documentId, textualContent, metadataJson);
                    }));
    }

    public Mono<Void> deleteDocument(Partition partition, DocumentId documentId) {
        return httpClient
            .delete()
            .uri(String.format(RAG_DOCUMENT_ENDPOINT, partition.partitionName(), documentId.asString()))
            .responseSingle((response, content) -> {
                int statusCode = response.status().code();

                if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
                    return content.then()
                        .doOnSuccess(any -> LOGGER.debug("Document {} is deleted from RAG context", documentId.asString()));
                }

                if (statusCode == HttpResponseStatus.NOT_FOUND.code()) {
                    return content.then()
                        .doOnSuccess(any -> LOGGER.debug("Document {} not found in partition {} while deleting from RAG context", documentId.asString(), partition.partitionName()));
                }

                return content.asString(StandardCharsets.UTF_8)
                    .defaultIfEmpty("")
                    .flatMap(body -> Mono.error(() -> new OpenRagUnexpectedException(
                        String.format("Failed to DELETE document %s: status %d and body %s", documentId.asString(), statusCode, body))));
            });
    }

    private Mono<String> sendFormRequest(HttpMethod method, String url, DocumentId documentId, String textualContent, String metadataJson) {
        return httpClient
            .headers(headers -> headers.add(CONTENT_TYPE_HEADER, "multipart/form-data"))
            .request(method)
            .uri(url)
            .sendForm((req, form) -> form
                .multipart(true)
                .attr("metadata", metadataJson)
                .file("file", documentId.asString() + ".txt", new ByteArrayInputStream(textualContent.getBytes(StandardCharsets.UTF_8)), "text/plain"))
            .responseSingle((res, content) -> getIndexationResponse(method, documentId, res, content));
    }

    private Mono<String> getIndexationResponse(HttpMethod method, DocumentId documentId, HttpClientResponse res, ByteBufMono content) {
        if (res.status().code() / 100 == 2) {
            return content.asString(StandardCharsets.UTF_8)
                .flatMap(contentString -> {
                    LOGGER.debug("document {} is added to RAG context ", documentId.asString());
                    return Mono.just(contentString);
                });
        } else if (res.status().code() == 409 && method == HttpMethod.POST) {
            return Mono.error(new DocumentConflictException());
        } else {
            return (Mono.error(new RuntimeException("Failed to " + method.name() + " document: " + documentId.asString() + " (status: " + res.status().code() + ")")));
        }
    }
}
