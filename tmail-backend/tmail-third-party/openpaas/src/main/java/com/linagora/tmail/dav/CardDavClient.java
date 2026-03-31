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

package com.linagora.tmail.dav;

import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class CardDavClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CardDavClient.class);
    private static final String COLLECTED_ADDRESS_BOOK_PATH = "/addressbooks/%s/collected/%s.vcf";
    private static final String ACCEPT_VCARD_JSON = "application/vcard+json";
    private static final String CONTENT_TYPE_VCARD = "application/vcard";

    private final HttpClient client;
    private final DavConfiguration config;

    CardDavClient(HttpClient client, DavConfiguration config) {
        this.client = client;
        this.config = config;
    }

    private UnaryOperator<HttpHeaders> cardDavHeaders(String username) {
        return headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_VCARD_JSON)
            .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username));
    }

    public Mono<Boolean> existsCollectedContact(String username, String userId, String collectedId) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(collectedId), "Collected id should not be empty");

        return client.headers(headers -> cardDavHeaders(username).apply(headers))
            .get()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, collectedId))
            .responseSingle((response, byteBufMono) -> handleContactExistsResponse(response, userId, collectedId));
    }

    private Mono<Boolean> handleContactExistsResponse(HttpClientResponse response, String userId, String collectedId) {
        return switch (response.status().code()) {
            case 200 -> Mono.just(true);
            case 404 -> Mono.just(false);
            default -> Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when checking contact exists for userId: %s and collectedId: %s",
                    response.status().code(), userId, collectedId)));
        };
    }

    public Mono<Void> createCollectedContact(String username, String userId, CardDavCreationObjectRequest request) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");

        return putCollectedContact(username, userId, request.uid(), request.toVCard().getBytes(StandardCharsets.UTF_8));
    }

    public Mono<Void> putCollectedContact(String username, String userId, String vcardUid, byte[] vcardPayload) {
        return client.headers(headers -> cardDavHeaders(username).apply(headers)
                .add(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_VCARD))
            .put()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, vcardUid))
            .send(Mono.just(Unpooled.wrappedBuffer(vcardPayload)))
            .responseSingle((response, byteBufMono) -> handleContactCreationResponse(response, userId, vcardUid));
    }

    private Mono<Void> handleContactCreationResponse(HttpClientResponse response, String userId, String vcardUid) {
        return switch (response.status().code()) {
            case 201 -> Mono.empty();
            case 204 -> {
                LOGGER.info("Contact for user {} and collected id {} already exists", userId, vcardUid);
                yield Mono.empty();
            }
            default -> Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when creating contact for user: %s and collected id: %s", response.status().code(), userId, vcardUid)));
        };
    }
}