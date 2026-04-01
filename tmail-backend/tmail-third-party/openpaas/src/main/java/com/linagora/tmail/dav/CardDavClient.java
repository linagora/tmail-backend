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

import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private UnaryOperator<HttpHeaders> cardDavHeaders(Username username) {
        return headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_VCARD_JSON)
            .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username.asString()));
    }

    public Mono<Boolean> existsCollectedContact(Username username, OpenPaaSUserId userId, DavUid collectedId) {
        return client.headers(headers -> cardDavHeaders(username).apply(headers))
            .get()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId.value(), collectedId.value()))
            .responseSingle((response, byteBufMono) -> handleContactExistsResponse(response, userId, collectedId));
    }

    private Mono<Boolean> handleContactExistsResponse(HttpClientResponse response, OpenPaaSUserId userId, DavUid collectedId) {
        return switch (response.status().code()) {
            case 200 -> Mono.just(true);
            case 404 -> Mono.just(false);
            default -> Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when checking contact exists for userId: %s and collectedId: %s",
                    response.status().code(), userId.value(), collectedId.value())));
        };
    }

    public Mono<Void> createCollectedContact(Username username, OpenPaaSUserId userId, CardDavCreationObjectRequest request) {
        return putCollectedContact(username, userId, request.uid(), request.toVCard().getBytes(StandardCharsets.UTF_8));
    }

    public Mono<Void> putCollectedContact(Username username, OpenPaaSUserId userId, DavUid vcardUid, byte[] vcardPayload) {
        return client.headers(headers -> cardDavHeaders(username).apply(headers)
                .add(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_VCARD))
            .put()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId.value(), vcardUid.value()))
            .send(Mono.just(Unpooled.wrappedBuffer(vcardPayload)))
            .responseSingle((response, byteBufMono) -> handleContactCreationResponse(response, userId, vcardUid));
    }

    private Mono<Void> handleContactCreationResponse(HttpClientResponse response, OpenPaaSUserId userId, DavUid vcardUid) {
        return switch (response.status().code()) {
            case 201 -> Mono.empty();
            case 204 -> {
                LOGGER.info("Contact for user {} and collected id {} already exists", userId.value(), vcardUid.value());
                yield Mono.empty();
            }
            default -> Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when creating contact for user: %s and collected id: %s", response.status().code(), userId.value(), vcardUid.value())));
        };
    }
}