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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;
import com.linagora.tmail.dav.request.GetCalendarByEventIdRequestBody;
import com.linagora.tmail.dav.xml.DavMultistatus;
import com.linagora.tmail.dav.xml.DavResponse;
import com.linagora.tmail.dav.xml.XMLUtil;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class DavClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DavClient.class);

    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String COLLECTED_ADDRESS_BOOK_PATH = "/addressbooks/%s/collected/%s.vcf";
    private static final Pattern CALENDAR_URI_PATTERN = Pattern.compile("/calendars/[^/]+/[^/]+/");
    private static final String ACCEPT_VCARD_JSON = "application/vcard+json";
    private static final String ACCEPT_XML = "application/xml";
    private static final String CONTENT_TYPE_VCARD = "application/vcard";

    private final HttpClient client;
    private final DavConfiguration config;

    public DavClient(DavConfiguration config) {
        this.config = config;
        this.client = createHttpClient(config.trustAllSslCerts().orElse(false));
    }

    private HttpClient createHttpClient(boolean trustAllSslCerts) {
        if (trustAllSslCerts) {
            return HttpClient.create()
                .baseUrl(config.baseUrl().toString())
                .responseTimeout(config.responseTimeout().orElse(DEFAULT_RESPONSE_TIMEOUT))
                .secure(sslContextSpec -> sslContextSpec.sslContext(
                    SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)));
        } else {
            return HttpClient.create()
                .baseUrl(config.baseUrl().toString())
                .responseTimeout(config.responseTimeout().orElse(DEFAULT_RESPONSE_TIMEOUT));
        }
    }

    private UsernamePasswordCredentials createDelegatedCredentials(String username) {
        return new UsernamePasswordCredentials(
            config.adminCredential().getUserName() + "&" + username,
            config.adminCredential().getPassword());
    }

    public Mono<Boolean> existsCollectedContact(String username, String userId, String collectedId) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(collectedId), "Collected id should not be empty");

        return client
            .headers(headers -> {
                headers.add(HttpHeaderNames.ACCEPT, ACCEPT_VCARD_JSON);
                headers.add(HttpHeaderNames.AUTHORIZATION,
                    HttpUtils.createBasicAuthenticationToken(createDelegatedCredentials(username)));
            })
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

        return client
            .headers(headers ->
                headers.add(HttpHeaderNames.ACCEPT, ACCEPT_VCARD_JSON)
                    .add(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_VCARD)
                    .add(HttpHeaderNames.AUTHORIZATION,
                        HttpUtils.createBasicAuthenticationToken(createDelegatedCredentials(username))))
            .put()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, request.uid()))
            .send(Mono.just(Unpooled.wrappedBuffer(request.toVCard().getBytes())))
            .responseSingle((response, byteBufMono) ->
                handleContactCreationResponse(response, userId, request));
    }

    private Mono<Void> handleContactCreationResponse(HttpClientResponse response, String userId, CardDavCreationObjectRequest request) {
        return switch (response.status().code()) {
            case 201 -> Mono.empty();
            case 204 -> {
                LOGGER.info("Contact for user {} and collected id {} already exists",
                    userId, request.uid());
                yield Mono.empty();
            }
            default -> Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when creating contact for user: %s and collected id: %s",
                    response.status().code(), userId, request.uid())));
        };
    }

    public Mono<Void> updateCalendarObject(String username, DavCalendarObject updatedCalendarObject) {
        return client.headers(headers ->
                headers.add(HttpHeaderNames.ACCEPT, ACCEPT_XML)
                    .add(HttpHeaderNames.AUTHORIZATION,
                        HttpUtils.createBasicAuthenticationToken(createDelegatedCredentials(username))))
            .request(HttpMethod.PUT)
            .uri(updatedCalendarObject.uri().toString())
            .send(Mono.just(Unpooled.wrappedBuffer(updatedCalendarObject.calendarData().toString().getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> {
                    if (response.status() == HttpResponseStatus.NO_CONTENT) {
                        return ReactorUtils.logAsMono(
                            () -> LOGGER.info("Calendar object '{}' updated successfully.", updatedCalendarObject.uri()));
                    } else {
                        return Mono.error(new DavClientException(
                            String.format(
                                "Unexpected status code: %d when updating calendar object '%s'",
                                response.status().code(), updatedCalendarObject.uri())));
                    }
                }
            );
    }

    public Mono<DavCalendarObject> getCalendarObjectContainingVEvent(String userId, String eventUid, String username) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(eventUid), "VEvent id should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");

        return findUserCalendars(userId, username)
            .flatMap(calendarURI ->
                getCalendarObjectContainingVEventFromSpecificCalendar(calendarURI, eventUid, username)
                    .switchIfEmpty(Mono.fromRunnable(
                        () -> LOGGER.trace("VEvent '{}' was not found in Calendar '{}'.", eventUid, calendarURI)))
                    .onErrorResume(ex -> {
                        LOGGER.debug("Error while querying '{}' for VEvent '{}': ",
                            calendarURI, eventUid, ex);
                        return Mono.empty();
                    }))
            .next();
    }

    private Mono<DavCalendarObject> getCalendarObjectContainingVEventFromSpecificCalendar(URI calendarURI, String eventUid, String username) {
        return client.headers(headers ->
                headers.add(HttpHeaderNames.ACCEPT, ACCEPT_XML)
                    .add("Depth", "1")
                    .add(HttpHeaderNames.AUTHORIZATION,
                        HttpUtils.createBasicAuthenticationToken(createDelegatedCredentials(username))))
            .request(HttpMethod.valueOf("REPORT"))
            .uri(calendarURI.getPath())
            .send(Mono.just(new GetCalendarByEventIdRequestBody(eventUid).asByteBuf()))
            .responseSingle(
                (response, responseContent) -> handleCalendarResponse(responseContent, response.status(), eventUid));
    }

    private Mono<DavCalendarObject> handleCalendarResponse(ByteBufMono responseContent, HttpResponseStatus responseStatus, String eventUid) {
        if (responseStatus == HttpResponseStatus.MULTI_STATUS) {
            return responseContent.asString(StandardCharsets.UTF_8)
                .map(content -> XMLUtil.parse(content, DavMultistatus.class))
                .map(this::extractCalendarObject)
                .flatMap(Mono::justOrEmpty);
        }
        return Mono.error(new DavClientException(
            String.format("Unexpected status code: %d when finding VCALENDAR object containing event: %s",
                responseStatus.code(), eventUid)));
    }

    private Optional<DavCalendarObject> extractCalendarObject(DavMultistatus multistatus) {
        return multistatus.getResponses()
            .stream()
            .findFirst()
            .flatMap(DavCalendarObject::fromDavResponse);
    }

    public Flux<URI> findUserCalendars(String userId, String username) {
        return client.headers(headers ->
                headers.add(HttpHeaderNames.ACCEPT, ACCEPT_XML)
                    .add(HttpHeaderNames.AUTHORIZATION,
                        HttpUtils.createBasicAuthenticationToken(createDelegatedCredentials(username))))
            .request(HttpMethod.valueOf("PROPFIND"))
            .uri("/calendars/" + userId)
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.MULTI_STATUS) {
                    return byteBufMono.asString(StandardCharsets.UTF_8)
                        .map(multiStatusResponse -> XMLUtil.parse(multiStatusResponse, DavMultistatus.class))
                        .map(this::extractCalendarURIsFromResponse);
                } else {
                    return Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when finding user calendars for user: %s",
                            response.status().code(), userId)));
                }
            })
            .flatMapMany(Flux::fromIterable);
    }

    private List<URI> extractCalendarURIsFromResponse(DavMultistatus multistatus) {
        List<URI> hrefs = new ArrayList<>();

        for (DavResponse response : multistatus.getResponses()) {
            response.getHref().getValue().ifPresent(href -> {
                if (CALENDAR_URI_PATTERN.matcher(href).matches() &&
                    !(href.endsWith("inbox/") || href.endsWith("outbox/"))) {
                    hrefs.add(URI.create(href));
                }
            });
        }

        LOGGER.trace("Found user calendars: {}", hrefs);
        return hrefs;
    }
}