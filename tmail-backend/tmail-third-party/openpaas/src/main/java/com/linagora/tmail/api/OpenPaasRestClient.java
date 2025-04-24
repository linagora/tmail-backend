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

package com.linagora.tmail.api;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import javax.net.ssl.SSLException;

import jakarta.mail.internet.AddressException;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.contact.UserSearchResponse;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class OpenPaasRestClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasRestClient.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private final HttpClient client;
    private final ObjectMapper deserializer = new ObjectMapper();
    private final UserSearchResponse.Deserializer userSearchResponseDeserializer = new UserSearchResponse.Deserializer();

    public OpenPaasRestClient(OpenPaasConfiguration openPaasConfiguration) throws SSLException {
        URI apiUrl = openPaasConfiguration.apirUri();
        String user = openPaasConfiguration.adminUsername();
        String password = openPaasConfiguration.adminPassword();

        this.client = createHttpClient(openPaasConfiguration, apiUrl, user, password);
    }

    private HttpClient createHttpClient(OpenPaasConfiguration openPaasConfiguration, URI apiUrl, String user, String password) throws SSLException {
        HttpClient baseHttpClient = HttpClient.create()
            .baseUrl(apiUrl.toString())
            .headers(headers -> headers.add(AUTHORIZATION_HEADER, HttpUtils.createBasicAuthenticationToken(user, password)))
            .responseTimeout(openPaasConfiguration.responseTimeout());

        if (openPaasConfiguration.trustAllSslCerts()) {
            SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            return baseHttpClient.secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        }

        return baseHttpClient;
    }

    public Mono<MailAddress> retrieveMailAddress(String openPaasUserId) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(openPaasUserId), "OpenPaas user id cannot be empty");
        return client.get()
            .uri(String.format("/users/%s", openPaasUserId))
            .responseSingle((statusCode, data) -> handleUserResponse(openPaasUserId, statusCode, data))
            .map(OpenPaasUserResponse::preferredEmail)
            .map(this::parseMailAddress)
            .onErrorResume(e -> Mono.error(new OpenPaasRestClientException("Failed to retrieve user mail using OpenPaas id " + openPaasUserId, e)));
    }

    public Mono<String> searchOpenPaasUserId(String email) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(email), "Email cannot be empty");

        return client.get()
            .uri(String.format("/users?email=%s", email))
            .responseSingle((statusCode, data) -> handleUserSearchResponse(email, statusCode, data))
            .map(UserSearchResponse::id)
            .onErrorResume(e -> {
                LOGGER.error("Failed to search OpenPaas user id by email {}", email, e);
                return Mono.empty();
            });
    }

    private Mono<UserSearchResponse> handleUserSearchResponse(String email, HttpClientResponse httpClientResponse, ByteBufMono dataBuf) {
        return switch (httpClientResponse.status().code()) {
            case 200 -> dataBuf.asByteArray()
                .flatMap(dataAsBytes -> Mono.fromCallable(() -> userSearchResponseDeserializer.deserialize(dataAsBytes))
                    .onErrorResume(e -> Mono.error(new OpenPaasRestClientException(
                        "Bad user response body format. Response: \n" + new String(dataAsBytes, StandardCharsets.UTF_8), e))))
                .flatMap(list -> switch (list.size()) {
                    case 1 -> Mono.just(list.getFirst()).filter(userSearchResponse -> email.equals(userSearchResponse.preferredEmail()));
                    case 0 -> {
                        LOGGER.info("Unable to retrieve OpenPaas user id as no user found with email {}", email);
                        yield Mono.empty();
                    }
                    default -> {
                        LOGGER.warn("Multiple users found with email {}. Records: {}",
                            email, list.stream().map(Record::toString).collect(Collectors.joining("\n ")));
                        yield Mono.empty();
                    }
                });
            default -> dataBuf.asString(StandardCharsets.UTF_8)
                .defaultIfEmpty("")
                .flatMap(errorResponse -> Mono.error(new OpenPaasRestClientException(
                    String.format("""
                                Response Status = %s,
                                Response Body = %s""", httpClientResponse.status().code(), errorResponse))));
        };
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
