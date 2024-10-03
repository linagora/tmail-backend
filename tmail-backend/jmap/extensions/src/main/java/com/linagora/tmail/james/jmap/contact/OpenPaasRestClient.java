package com.linagora.tmail.james.jmap.contact;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class OpenPaasRestClient {
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final OpenPaasConfiguration openPaasConfiguration;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenPaasRestClient(OpenPaasConfiguration openPaasConfiguration) {
        this.openPaasConfiguration = openPaasConfiguration;
        this.client = HttpClient.create()
            .baseUrl(openPaasConfiguration.getWebClientBaseUrl().toString())
            .headers(headers -> headers.add(AUTHORIZATION_HEADER, basicAuthenticationHeaderValue()))
            .responseTimeout(RESPONSE_TIMEOUT);
    }

    public Mono<OpenPaasUserResponse> getUserById(String openPaasUserId) {
        return client.get()
            .uri(String.format("/users/%s", openPaasUserId))
            .responseContent()
            .aggregate()
            .asString(StandardCharsets.UTF_8)
            .handle((content, sink) -> {
                try {
                    sink.next(objectMapper.readValue(content, OpenPaasUserResponse.class));
                } catch (JsonProcessingException e) {
                    sink.error(new RuntimeException(e));
                }
            });
    }

    private String basicAuthenticationHeaderValue() {
        String userPassword = openPaasConfiguration.getWebClientUser() + ":" + openPaasConfiguration.getWebClientPassword();
        byte[] base64UserPassword = Base64.getEncoder().encode(userPassword.getBytes(StandardCharsets.UTF_8));

        return "Basic " + new String(base64UserPassword, StandardCharsets.UTF_8);
    }

}
