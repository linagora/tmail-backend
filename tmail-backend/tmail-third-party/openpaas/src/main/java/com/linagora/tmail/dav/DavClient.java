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
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLException;

import jakarta.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.james.core.Username;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.linagora.tmail.HttpUtils;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;
import com.linagora.tmail.dav.request.GetCalendarByEventIdRequestBody;
import com.linagora.tmail.dav.xml.DavMultistatus;
import com.linagora.tmail.dav.xml.DavResponse;
import com.linagora.tmail.dav.xml.XMLUtil;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import net.fortuna.ical4j.model.Calendar;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.retry.Retry;

public class DavClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DavClient.class);
    private static final String COLLECTED_ADDRESS_BOOK_PATH = "/addressbooks/%s/collected/%s.vcf";
    private static final String ACCEPT_VCARD_JSON = "application/vcard+json";
    private static final String ACCEPT_XML = "application/xml";
    private static final String CONTENT_TYPE_VCARD = "application/vcard";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String PRINCIPAL_BODY_REQUEST = """
        <d:propfind xmlns:d="DAV:">
          <d:prop>
             <d:current-user-principal />
          </d:prop>
        </d:propfind>""";

    public static final int MAX_CALENDAR_OBJECT_UPDATE_RETRIES = 5;
    public static final String CALENDAR_PATH = "/calendars/";

    private final HttpClient client;
    private final DavConfiguration config;
    private static final Duration calendarObjectUpdateRetryBackoff = Optional.ofNullable(System.getProperty("MIN_CALENDAR_OBJECT_UPDATE_RETRY_BACKOFF_IN_MILLS"))
            .map(Long::parseLong)
            .map(Duration::ofMillis)
            .orElse(Duration.ofMillis(100));

    @Inject
    public DavClient(DavConfiguration config) throws SSLException {
        this.config = config;
        this.client = createHttpClient();
    }

    private HttpClient createHttpClient() throws SSLException {
        HttpClient client = HttpClient.create()
            .baseUrl(config.baseUrl().toString())
            .responseTimeout(config.responseTimeout());
        if (config.trustAllSslCerts()) {
            SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            return client.secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));
        }
        return client;
    }

    private String authenticationToken(String username) {
        return HttpUtils.createBasicAuthenticationToken(new UsernamePasswordCredentials(
            config.adminCredential().getUserName() + "&" + username,
            config.adminCredential().getPassword()));
    }

    public Mono<Boolean> existsCollectedContact(String username, String userId, String collectedId) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(collectedId), "Collected id should not be empty");

        return client.headers(headers -> addCardDavHeaders(username).apply(headers))
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
        return client.headers(headers -> addCardDavHeaders(username).apply(headers)
                .add(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_VCARD))
            .put()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, vcardUid))
            .send(Mono.just(Unpooled.wrappedBuffer(vcardPayload)))
            .responseSingle((response, byteBufMono) -> handleContactCreationResponse(response, userId, vcardUid));
    }

    private UnaryOperator<HttpHeaders> addCardDavHeaders(String username) {
        return headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_VCARD_JSON)
            .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(username));
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

    public Mono<Void> updateCalendarObject(DavUser user, URI calendarObjectUri, UnaryOperator<DavCalendarObject> calendarObjectUpdater) {
        return getCalendarObjectByUri(user, calendarObjectUri)
            .map(calendarObjectUpdater)
            .flatMap(updatedCalendarObject -> doUpdateCalendarObject(user.username(), updatedCalendarObject))
            .retryWhen(Retry.backoff(MAX_CALENDAR_OBJECT_UPDATE_RETRIES, calendarObjectUpdateRetryBackoff)
                .filter(RetriableDavClientException.class::isInstance)
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                    new DavClientException("Max retries exceeded for calendar update", retrySignal.failure())));
    }

    public Mono<Void> doUpdateCalendarObject(String username, DavCalendarObject updatedCalendarObject) {
        return client.headers(headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_XML)
                .add(HttpHeaderNames.IF_MATCH, updatedCalendarObject.eTag())
                .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(username)))
            .request(HttpMethod.PUT)
            .uri(updatedCalendarObject.uri().toString())
            .send(Mono.just(Unpooled.wrappedBuffer(updatedCalendarObject.calendarData().toString().getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> handleCalendarObjectUpdateResponse(updatedCalendarObject, response));
    }

    public Mono<Void> createCalendar(String username, URI uri, Calendar calendarData) {
        return client.headers(headers -> headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(username)))
            .request(HttpMethod.PUT)
            .uri(uri.toString())
            .send(Mono.just(Unpooled.wrappedBuffer(calendarData.toString().getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> {
                switch (response.status().code()) {
                    case 201:
                        return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar object '{}' created successfully.", uri));
                    default:
                        return responseContent.asString(StandardCharsets.UTF_8)
                            .switchIfEmpty(Mono.just(StringUtils.EMPTY))
                            .flatMap(responseBody -> Mono.error(new DavClientException("""
                                Unexpected status code: %d when create calendar object '%s'
                                %s
                                """.formatted(response.status().code(), uri.toString(), responseBody))));

                }
            });
    }

    public Mono<Void> deleteCalendar(String username, URI uri) {
        return client.headers(headers -> headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(username)))
            .request(HttpMethod.DELETE)
            .uri(uri.toString())
            .responseSingle((response, responseContent) -> {
                switch (response.status().code()) {
                    case 204:
                        return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar object '{}' has been deleted successfully.", uri));
                    default:
                        return responseContent.asString(StandardCharsets.UTF_8)
                            .switchIfEmpty(Mono.just(StringUtils.EMPTY))
                            .flatMap(responseBody -> Mono.error(new DavClientException("""
                                Unexpected status code: %d when deleting calendar object '%s'
                                %s
                                """.formatted(response.status().code(), uri.toString(), responseBody))));

                }
            });
    }

    public Mono<Void> sendITIPRequest(String username, URI uri, byte[] json) {
        return client.headers(headers -> headers.add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(username)))
            .request(HttpMethod.valueOf("ITIP"))
            .uri(uri.toString())
            .send(Mono.just(Unpooled.wrappedBuffer(json)))
            .responseSingle((response, responseContent) -> {
                switch (response.status().code()) {
                    case 204:
                        return ReactorUtils.logAsMono(() -> LOGGER.info("Send itip request for '{}' successfully.", uri));
                    default:
                        return responseContent.asString(StandardCharsets.UTF_8)
                            .switchIfEmpty(Mono.just(StringUtils.EMPTY))
                            .flatMap(responseBody -> Mono.error(new DavClientException("""
                                Unexpected status code: %d when sending itip request for '%s'
                                %s
                                """.formatted(response.status().code(), uri.toString(), responseBody))));

                }
            });
    }

    private static Mono<Void> handleCalendarObjectUpdateResponse(DavCalendarObject updatedCalendarObject, HttpClientResponse response) {
        if (response.status() == HttpResponseStatus.NO_CONTENT) {
            return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar object '{}' updated successfully.", updatedCalendarObject.uri()));
        } else if (response.status() == HttpResponseStatus.PRECONDITION_FAILED) {
            return Mono.error(new RetriableDavClientException(
                String.format("Precondition failed (ETag mismatch) when updating calendar object '%s'. Retry may be needed.", updatedCalendarObject.uri())));
        } else {
            return Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when updating calendar object '%s'", response.status().code(), updatedCalendarObject.uri())));
        }
    }

    public Mono<DavCalendarObject> getCalendarObjectByUri(DavUser user, URI uri) {
        return client.headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION, authenticationToken(user.username())))
            .request(HttpMethod.GET)
            .uri(uri.toString())
            .responseSingle((response, responseContent) -> {
                    if (response.status() == HttpResponseStatus.OK) {
                        return responseContent.asInputStream()
                            .map(CalendarEventParsed::parseICal4jCalendar)
                            .map(calendar -> new DavCalendarObject(uri, calendar, response.responseHeaders().get("ETag")));
                    }
                    return Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when fetching calendar object '%s'", response.status().code(), uri)));
            });
    }

    public Mono<DavCalendarObject> getCalendarObject(DavUser user, EventUid eventUid) {
        Preconditions.checkNotNull(user, "Dav user should not be null");

        return findUserCalendars(user)
            .flatMap(calendarURI -> getCalendarObjectContainingVEventFromSpecificCalendar(calendarURI, eventUid, user.username())
                    .switchIfEmpty(ReactorUtils.logAsMono(() -> LOGGER.trace("VEvent '{}' was not found in Calendar '{}'.", eventUid, calendarURI))
                            .then(Mono.empty()))
                    .onErrorResume(ex -> {
                        LOGGER.debug("Error while querying '{}' for VEvent '{}': ", calendarURI, eventUid, ex);
                        return Mono.empty();
                    }))
            .next();
    }

    private Mono<DavCalendarObject> getCalendarObjectContainingVEventFromSpecificCalendar(URI calendarURI, EventUid eventUid, String username) {
        return client.headers(headers -> calDavHeaders(username).apply(headers)
                    .add("Depth", "1"))
            .request(HttpMethod.valueOf("REPORT"))
            .uri(calendarURI.getPath())
            .send(Mono.just(new GetCalendarByEventIdRequestBody(eventUid).asByteBuf()))
            .responseSingle((response, responseContent) -> handleCalendarResponse(responseContent, response.status(), eventUid));
    }

    private Mono<DavCalendarObject> handleCalendarResponse(ByteBufMono responseContent, HttpResponseStatus responseStatus, EventUid eventUid) {
        if (responseStatus == HttpResponseStatus.MULTI_STATUS) {
            return responseContent.asString(StandardCharsets.UTF_8)
                .map(content -> XMLUtil.parse(content, DavMultistatus.class))
                .map(this::extractCalendarObject)
                .flatMap(Mono::justOrEmpty);
        }
        return Mono.error(new DavClientException(
            String.format("Unexpected status code: %d when finding VCALENDAR object containing event: %s", responseStatus.code(), eventUid.value())));
    }

    private Optional<DavCalendarObject> extractCalendarObject(DavMultistatus multistatus) {
        return multistatus.getResponses()
            .stream()
            .findFirst()
            .flatMap(DavCalendarObject::fromDavResponse);
    }

    public Flux<URI> findUserCalendars(DavUser user) {
        return client.headers(headers -> calDavHeaders(user.username()).apply(headers))
            .request(HttpMethod.valueOf("PROPFIND"))
            .uri(CALENDAR_PATH + user.userId())
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.MULTI_STATUS) {
                    return byteBufMono.asString(StandardCharsets.UTF_8)
                        .map(multiStatusResponse -> XMLUtil.parse(multiStatusResponse, DavMultistatus.class))
                        .map(this::extractCalendarURIsFromResponse);
                } else {
                    return Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when finding user calendars for user: %s",
                            response.status().code(), user.userId())));
                }
            })
            .flatMapMany(Flux::fromIterable);
    }

    private UnaryOperator<HttpHeaders> calDavHeaders(String username) {
        return headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_XML)
            .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(username));
    }

    private List<URI> extractCalendarURIsFromResponse(DavMultistatus multistatus) {
        return multistatus.getResponses().stream()
            .filter(DavResponse::isCalendarCollectionResponse)
            .flatMap(response -> response.getHref()
                .getValue()
                .filter(href -> !(href.endsWith("inbox/") || href.endsWith("outbox/")))
                .flatMap(this::parseCalendarHref).stream())
            .peek(href -> LOGGER.trace("Found user calendar: '{}'", href))
            .toList();
    }

    private Optional<URI> parseCalendarHref(String href) {
        try {
            return Optional.of(URI.create(href));
        } catch (RuntimeException e) {
            LOGGER.trace("Found an invalid calendar href in Dav server response '{}'", href);
            return Optional.empty();
        }
    }

    public Mono<FreeBusyResponse> freeBusyQuery(DavUser user, FreeBusyRequest request) {
        return client.headers(headers -> headers.add(HttpHeaderNames.ACCEPT, CONTENT_TYPE_JSON)
                .add(HttpHeaderNames.AUTHORIZATION, authenticationToken(user.username())))
            .request(HttpMethod.POST)
            .uri(CALENDAR_PATH + "freebusy")
            .send(Mono.just(Unpooled.wrappedBuffer(request.serializeAsBytes())))
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.OK) {
                    return byteBufMono.asByteArray().map(FreeBusyResponse::deserialize);
                }
                return byteBufMono.asString(StandardCharsets.UTF_8)
                    .switchIfEmpty(Mono.just(StringUtils.EMPTY))
                    .flatMap(body -> Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when querying freebusy for user: %s. Response body: %s",
                            response.status().code(), user.userId(), body)
                    )));
            });
    }

    public Mono<Void> getPrincipal(Username user) {
        return client.headers(headers -> calDavHeaders(user.asString()).apply(headers)
                .add("Depth", "0"))
            .request(HttpMethod.valueOf("PROPFIND"))
            .uri("/")
            .send(Mono.just(Unpooled.wrappedBuffer(PRINCIPAL_BODY_REQUEST.getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.MULTI_STATUS) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("User {} has been auto-provisioned successfully via principal endpoint.", user.asString()));
                } else {
                    return Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when finding principal for user: %s",
                            response.status().code(), user.asString())));
                }
            });
    }
}