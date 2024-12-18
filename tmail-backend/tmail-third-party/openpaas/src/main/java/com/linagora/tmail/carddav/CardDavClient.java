package com.linagora.tmail.carddav;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.linagora.tmail.HttpUtils;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

public interface CardDavClient {

    Mono<Boolean> existsCollectedContact(String openPaasUserId, String collectedId);

    Mono<Void> createCollectedContact(String openPaasUserId, CardDavCreationObjectRequest creationObjectRequest);

    class DefaultImpl implements CardDavClient {
        public record CardDavConfiguration(UsernamePasswordCredentials adminCredential,
                                           URI baseUrl,
                                           Optional<Boolean> trustAllSslCerts,
                                           Optional<Duration> responseTimeout) {
            static boolean CLIENT_TRUST_ALL_SSL_CERTS_DISABLED = false;

            public static CardDavConfiguration from(Configuration configuration) {
                UsernamePasswordCredentials adminCredential = new UsernamePasswordCredentials(
                    configuration.getString("carddav.admin.user"),
                    configuration.getString("carddav.admin.password"));
                URI baseUrl = URI.create(configuration.getString("carddav.api.uri"));
                Optional<Boolean> trustAllSslCerts = Optional.of(configuration.getBoolean("carddav.rest.client.trust.all.ssl.certs", CLIENT_TRUST_ALL_SSL_CERTS_DISABLED));
                Optional<Duration> responseTimeout = Optional.ofNullable(configuration.getDuration("carddav.rest.client.response.timeout"))
                    .map(duration -> {
                        Preconditions.checkArgument(!duration.isNegative(), "Response timeout should not be negative");
                        return duration;
                    });
                return new CardDavConfiguration(adminCredential, baseUrl, trustAllSslCerts, responseTimeout);
            }
        }

        private static final Logger LOGGER = LoggerFactory.getLogger(DefaultImpl.class);
        private static final Duration RESPONSE_TIMEOUT_DEFAULT = Duration.ofSeconds(10);
        private static final String COLLECTED_ADDRESS_BOOK_PATH = "/addressbooks/%s/collected/%s.vcf";

        private final HttpClient client;
        private final CardDavCreationSerializer serializer;

        public DefaultImpl(CardDavConfiguration cardDavConfiguration) {
            this.client = HttpClient.create()
                .baseUrl(cardDavConfiguration.baseUrl.toString())
                .headers(headers -> {
                    headers.add(HttpHeaderNames.AUTHORIZATION, HttpUtils.createBasicAuthenticationToken(cardDavConfiguration.adminCredential));
                    headers.add(HttpHeaderNames.ACCEPT, "application/vcard+json");
                })
                .responseTimeout(cardDavConfiguration.responseTimeout.orElse(RESPONSE_TIMEOUT_DEFAULT));

            cardDavConfiguration.trustAllSslCerts.ifPresent(trustAllSslCerts ->
                client.secure(sslContextSpec -> sslContextSpec.sslContext(
                    SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE))));

            this.serializer = new CardDavCreationSerializer();
        }

        @Override
        public Mono<Boolean> existsCollectedContact(String openPaasUserId, String collectedId) {
            Preconditions.checkArgument(StringUtils.isEmpty(openPaasUserId), "OpenPaas user id should not be empty");
            Preconditions.checkArgument(StringUtils.isEmpty(collectedId), "Collected id should not be empty");

            return client.get()
                .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, openPaasUserId, collectedId))
                .responseSingle((response, byteBufMono) -> switch (response.status().code()) {
                    case 200 -> Mono.just(true);
                    case 404 -> Mono.just(false);
                    default -> {
                        LOGGER.warn("Unexpected status code: {}, when checking contact exists for user {} and collected id {}",
                            response.status().code(), openPaasUserId, collectedId);
                        yield Mono.just(false);
                    }
                });
        }

        @Override
        public Mono<Void> createCollectedContact(String openPaasUserId, CardDavCreationObjectRequest creationObjectRequest) {
            return client.put()
                .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, openPaasUserId, creationObjectRequest.uid()))
                .send(Mono.fromCallable(() -> serializer.serializeAsBytes(creationObjectRequest))
                    .map(Unpooled::wrappedBuffer))
                .responseSingle((response, byteBufMono) -> switch (response.status().code()) {
                    case 201 -> Mono.empty();
                    case 204 -> {
                        LOGGER.info("Contact for user {} and collected id {} already exists", openPaasUserId, creationObjectRequest.uid());
                        yield Mono.empty();
                    }
                    default -> {
                        LOGGER.warn("Unexpected status code: {}, when creating contact for user {} and collected id {}",
                            response.status().code(), openPaasUserId, creationObjectRequest.uid());
                        yield Mono.error(new RuntimeException("Unexpected status code: " + response.status().code()));
                    }
                });
        }
    }
}
