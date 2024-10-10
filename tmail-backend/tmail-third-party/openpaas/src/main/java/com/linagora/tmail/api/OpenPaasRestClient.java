package com.linagora.tmail.api;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.OpenPaasConfiguration;

import io.netty.handler.codec.http.HttpResponseStatus;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class OpenPaasRestClient {
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final HttpClient client;
    private final ObjectMapper deserializer = new ObjectMapper();

    public OpenPaasRestClient(OpenPaasConfiguration openPaasConfiguration) {
        String user = openPaasConfiguration.getWebClientUser();
        String password = openPaasConfiguration.getWebClientPassword();
        this.client = HttpClient.create()
            .baseUrl(openPaasConfiguration.getWebClientBaseUrl().toString())
            .headers(headers -> headers.add(AUTHORIZATION_HEADER, HttpUtils.createBasicAuthenticationToken(user, password)))
            .responseTimeout(RESPONSE_TIMEOUT);
    }

    public Mono<Optional<OpenPaasUserResponse>> getUserById(String openPaasUserId) {
        return client.get()
            .uri(String.format("/users/%s", openPaasUserId))
            .responseSingle(this::afterHTTPResponseGetUserByIdHandler);
    }

    private Mono<Optional<OpenPaasUserResponse>> afterHTTPResponseGetUserByIdHandler(HttpClientResponse httpClientResponse, ByteBufMono dataBuf) {
        return Mono.just(httpClientResponse.status())
            .filter(httpStatus -> httpStatus.equals(HttpResponseStatus.OK))
            .flatMap(httpStatus -> dataBuf.asByteArray())
            .map(content -> Optional.of(parseUserResponseContent(content)))
            .onErrorResume(e -> Mono.error(new OpenPaasRestClientException("Bad response body format", e)))
            .switchIfEmpty(Mono.just(httpClientResponse.status())
                .filter(HttpResponseStatus.NOT_FOUND::equals)
                .flatMap(httpNotFound -> Mono.just(Optional.empty())));
    }

    private OpenPaasUserResponse parseUserResponseContent(byte[] contentBytes) {
        try {
            return deserializer.readValue(new String(contentBytes, StandardCharsets.UTF_8), OpenPaasUserResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
