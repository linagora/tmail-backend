package com.linagora.tmail.api;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.OpenPaasConfiguration;

import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class OpenPaasRestClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasRestClient.class);

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final HttpClient client;
    private final ObjectMapper deserializer = new ObjectMapper();

    public OpenPaasRestClient(OpenPaasConfiguration openPaasConfiguration) {
        URL apiUrl = openPaasConfiguration.restClientUrl();
        String user = openPaasConfiguration.restClientUser();
        String password = openPaasConfiguration.restClientPassword();
        this.client = HttpClient.create()
            .baseUrl(apiUrl.toString())
            .headers(headers -> headers.add(AUTHORIZATION_HEADER, HttpUtils.createBasicAuthenticationToken(user, password)))
            .responseTimeout(RESPONSE_TIMEOUT);
    }

    public Mono<MailAddress> retrieveMailAddress(String openPaasUserId) {
        return client.get()
            .uri(String.format("/users/%s", openPaasUserId))
            .responseSingle((statusCode, data) -> handleUserResponse(openPaasUserId, statusCode, data))
            .map(OpenPaasUserResponse::preferredEmail)
            .map(this::parseMailAddress)
            .onErrorResume(e -> Mono.error(new OpenPaasRestClientException("Failed to retrieve user mail using OpenPaas id", e)));
    }

    private Mono<OpenPaasUserResponse> handleUserResponse(String openPaasUserId, HttpClientResponse httpClientResponse, ByteBufMono dataBuf) {
        int statusCode = httpClientResponse.status().code();

        return switch (statusCode) {
            case 200 -> dataBuf.asByteArray()
                .map(this::parseUserResponse)
                .onErrorResume(e -> Mono.error(new OpenPaasRestClientException("Bad user response body format", e)));
            case 404 -> {
                LOGGER.warn("Unable to retrieve mail address as OpenPaas user with id {} not found", openPaasUserId);
                yield Mono.empty();
            }
            default -> dataBuf.asString(StandardCharsets.UTF_8)
                .switchIfEmpty(Mono.just(""))
                .flatMap(errorResponse -> Mono.error(new OpenPaasRestClientException(
                    String.format("""
                            Error when getting OpenPaas user response.\s
                            Response Status = %s,
                            Response Body = %s""", statusCode, errorResponse))));
        };
    }

    private OpenPaasUserResponse parseUserResponse(byte[] responseAsBytes) {
        try {
            return deserializer.readValue(new String(responseAsBytes, StandardCharsets.UTF_8), OpenPaasUserResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private MailAddress parseMailAddress(String email) {
        try {
            return new MailAddress(email);
        } catch (AddressException e) {
           throw new RuntimeException(e);
        }
    }
}
