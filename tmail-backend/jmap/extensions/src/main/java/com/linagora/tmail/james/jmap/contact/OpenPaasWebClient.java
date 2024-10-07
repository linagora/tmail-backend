package com.linagora.tmail.james.jmap.contact;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public class OpenPaasWebClient {
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final OpenPaasConfiguration openPaasConfiguration;
    private final HttpClient client;

    public OpenPaasWebClient(OpenPaasConfiguration openPaasConfiguration) {
        this.openPaasConfiguration = openPaasConfiguration;
        this.client = HttpClient.create()
            .baseUrl(openPaasConfiguration.getWebClientBaseUrl().toString())
            .headers(headers -> headers.add(AUTHORIZATION_HEADER, basicAuthenticationHeaderValue()))
            .responseTimeout(RESPONSE_TIMEOUT);
    }

    public Mono<List<OpenPaasUserResponse>> getUserById(String openPaasUserId) {
        return client.get()
            .uri(String.format("/users/%s", openPaasUserId))
            .response()
            .cast(OpenPaasUserResponse[].class)
            .map(userResponseArray -> Arrays.stream(userResponseArray).toList());
    }

    private String basicAuthenticationHeaderValue() {
        String userPassword = openPaasConfiguration.getWebClientUser() + ":" + openPaasConfiguration.getWebClientPassword();
        byte[] base64UserPassword = Base64.getEncoder().encode(userPassword.getBytes(StandardCharsets.UTF_8));

        return "Basic " + new String(base64UserPassword, StandardCharsets.UTF_8);
    }

}
