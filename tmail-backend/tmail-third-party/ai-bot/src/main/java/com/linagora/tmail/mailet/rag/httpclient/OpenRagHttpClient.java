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

import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.linagora.tmail.mailet.rag.RagConfig;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class OpenRagHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenRagHttpClient.class);
    private static final String API_KEY_HEADER = "Authorization";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String CHAT_COMPLETIONS_ENDPOINT = "/v1/chat/completions";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenRagHttpClient(RagConfig configuration) {
        this.objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
        this.httpClient = buildReactorNettyHttpClient(configuration);
    }

    public Mono<String> addDocument(Partition partition, DocumentId documentId, String textualContent, Map<String, String> metadata) {
        String url = String.format("/indexer/partition/%s/file/%s", partition.partitionName(), documentId.asString());
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(metadata))
            .flatMap(metadataJson ->
                sendFormRequest(HttpMethod.POST, url, documentId, textualContent, metadataJson)
                    .onErrorResume(DocumentConflictException.class, throwable -> {
                        LOGGER.debug("Document already exists. Retrying with PUT.");
                        return sendFormRequest(HttpMethod.PUT, url, documentId, textualContent, metadataJson);
                    }));
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

    public Mono<ChatCompletionResult> proxyChatCompletions(byte[] payload) {
        return httpClient
            .headers(headers -> headers.add(CONTENT_TYPE_HEADER, APPLICATION_JSON))
            .post()
            .uri(CHAT_COMPLETIONS_ENDPOINT)
            .send(Mono.just(Unpooled.wrappedBuffer(payload)))
            .responseSingle((response, content) -> content
                .asByteArray()
                .map(body -> new ChatCompletionResult(body, response.status().code(), response.responseHeaders())));
    }

    private static SslContext buildInsecureSslContext() {
        try {
            return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        } catch (SSLException e) {
            throw new RuntimeException("Failed to create insecure SSL context", e);
        }
    }

    private HttpClient buildReactorNettyHttpClient(RagConfig configuration) {
        HttpClient client = HttpClient.create()
            .baseUrl(configuration.getBaseURLOpt().get().toString())
            .headers(headers -> headers.add(API_KEY_HEADER, "Bearer " + configuration.getAuthorizationToken())
                .add("Accept", APPLICATION_JSON));
        return (configuration.getTrustAllCertificates() ? client.secure(spec -> spec.sslContext(buildInsecureSslContext())) : client.secure());
    }

}
