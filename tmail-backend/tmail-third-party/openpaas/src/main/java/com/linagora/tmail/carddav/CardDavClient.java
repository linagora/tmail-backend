package com.linagora.tmail.carddav;

import java.time.Duration;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.CardDavConfiguration;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public interface CardDavClient {

    Mono<Boolean> existsCollectedContact(String username, String userId, String collectedId);

    Mono<Void> createCollectedContact(String username, String userId, CardDavCreationObjectRequest creationObjectRequest);

    class OpenpaasCardDavClient implements CardDavClient {

        private static final Logger LOGGER = LoggerFactory.getLogger(OpenpaasCardDavClient.class);
        private static final Duration RESPONSE_TIMEOUT_DEFAULT = Duration.ofSeconds(10);
        private static final String COLLECTED_ADDRESS_BOOK_PATH = "/addressbooks/%s/collected/%s.vcf";

        private final HttpClient client;

        private final Function<String, UsernamePasswordCredentials> adminCredentialWithDelegatedFunction;

        public OpenpaasCardDavClient(CardDavConfiguration cardDavConfiguration) {
            this.client = HttpClient.create()
                .baseUrl(cardDavConfiguration.baseUrl().toString())
                .headers(headers -> headers.add(HttpHeaderNames.ACCEPT, "application/vcard+json"))
                .responseTimeout(cardDavConfiguration.responseTimeout().orElse(RESPONSE_TIMEOUT_DEFAULT));

            cardDavConfiguration.trustAllSslCerts().ifPresent(trustAllSslCerts ->
                client.secure(sslContextSpec -> sslContextSpec.sslContext(
                    SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE))));

            this.adminCredentialWithDelegatedFunction = openPaasUsername -> new UsernamePasswordCredentials(cardDavConfiguration.adminCredential().getUserName() + "&" + openPaasUsername,
                cardDavConfiguration.adminCredential().getPassword());
        }

        @Override
        public Mono<Boolean> existsCollectedContact(String username, String userId, String collectedId) {
            Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");
            Preconditions.checkArgument(StringUtils.isNotEmpty(collectedId), "Collected id should not be empty");

            return client
                .headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION, HttpUtils.createBasicAuthenticationToken(adminCredentialWithDelegatedFunction.apply(username))))
                .get()
                .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, collectedId))
                .responseSingle((response, byteBufMono) -> switch (response.status().code()) {
                    case 200 -> Mono.just(true);
                    case 404 -> Mono.just(false);
                    default ->
                        Mono.error(new CardDavClientException("Unexpected status code: " + response.status().code()
                            + " when checking contact exists for openPaasUserId: " + userId + " and collectedId: " + collectedId));
                });
        }

        @Override
        public Mono<Void> createCollectedContact(String username, String userId, CardDavCreationObjectRequest creationObjectRequest) {
            return client
                .headers(headers -> {
                    headers.add(HttpHeaderNames.CONTENT_TYPE, "text/vcard");
                    headers.add(HttpHeaderNames.AUTHORIZATION, HttpUtils.createBasicAuthenticationToken(adminCredentialWithDelegatedFunction.apply(username)));
                })
                .put()
                .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, creationObjectRequest.uid()))
                .send(Mono.just(Unpooled.wrappedBuffer(creationObjectRequest.toVCard().getBytes())))
                .responseSingle((response, byteBufMono) -> switch (response.status().code()) {
                    case 201 -> Mono.empty();
                    case 204 -> {
                        LOGGER.info("Contact for user {} and collected id {} already exists", userId, creationObjectRequest.uid());
                        yield Mono.empty();
                    }
                    default ->
                        Mono.error(new CardDavClientException("Unexpected status code: " + response.status().code()
                            + " when creating contact for user: " + userId + " and collected id: " + creationObjectRequest.uid()));
                });
        }
    }
}
