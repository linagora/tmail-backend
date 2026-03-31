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

import org.apache.james.core.Username;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
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
import net.fortuna.ical4j.model.Calendar;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.util.retry.Retry;

public class CalDavClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CalDavClient.class);
    private static final String ACCEPT_XML = "application/xml";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String PRINCIPAL_BODY_REQUEST = """
        <d:propfind xmlns:d="DAV:">
          <d:prop>
             <d:current-user-principal />
          </d:prop>
        </d:propfind>""";
    
    public static final int MAX_CALENDAR_OBJECT_UPDATE_RETRIES = 5;
    public static final String CALENDAR_PATH = "/calendars/";

    private static final Duration calendarObjectUpdateRetryBackoff = Optional.ofNullable(System.getProperty("MIN_CALENDAR_OBJECT_UPDATE_RETRY_BACKOFF_IN_MILLS"))
        .map(Long::parseLong)
        .map(Duration::ofMillis)
        .orElse(Duration.ofMillis(100));

    private final HttpClient client;
    private final DavConfiguration config;

    CalDavClient(HttpClient client, DavConfiguration config) {
        this.client = client;
        this.config = config;
    }

    private UnaryOperator<HttpHeaders> calDavHeaders(String username) {
        return headers -> headers.add(HttpHeaderNames.ACCEPT, ACCEPT_XML)
            .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username));
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
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username)))
            .request(HttpMethod.PUT)
            .uri(updatedCalendarObject.uri().toString())
            .send(Mono.just(Unpooled.wrappedBuffer(updatedCalendarObject.calendarData().toString().getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> handleCalendarObjectUpdateResponse(updatedCalendarObject, response));
    }

    private static Mono<Void> handleCalendarObjectUpdateResponse(DavCalendarObject updatedCalendarObject, HttpClientResponse response) {
        if (response.status() == HttpResponseStatus.NO_CONTENT) {
            return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar object '{}' updated successfully.", updatedCalendarObject.uri()));
        } else if (response.status() == HttpResponseStatus.PRECONDITION_FAILED) {
            return Mono.error(new RetriableDavClientException(
                String.format("Precondition failed (ETag mismatch) when updating calendar object '%s'. Retry may be needed.", updatedCalendarObject.uri())));
        } else if (response.status() == HttpResponseStatus.FORBIDDEN) {
            return Mono.error(new DavClientException.PermissionDenied(
                String.format("Permission denied (403) when updating calendar object '%s'", updatedCalendarObject.uri())));
        } else {
            return Mono.error(new DavClientException(
                String.format("Unexpected status code: %d when updating calendar object '%s'", response.status().code(), updatedCalendarObject.uri())));
        }
    }

    public Mono<Void> grantCalendarDelegation(DavUser calendarOwner, String delegatedToEmail) {
        String uri = CALENDAR_PATH + calendarOwner.userId() + "/" + calendarOwner.userId() + ".json";
        String payload = """
            {
              "share": {
                "set": [
                  {
                    "dav:href": "mailto:%s",
                    "dav:read": true
                  }
                ],
                "remove": []
              }
            }
            """.formatted(delegatedToEmail);

        return client.headers(headers -> headers
                .add(HttpHeaderNames.CONTENT_TYPE, "application/json;charset=UTF-8")
                .add(HttpHeaderNames.ACCEPT, "application/json, text/plain, */*")
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(calendarOwner.username())))
            .request(HttpMethod.POST)
            .uri(uri)
            .send(Mono.just(Unpooled.wrappedBuffer(payload.getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> {
                if (response.status().code() == 200) {
                    return Mono.empty();
                }
                return DavClientHelper.unexpectedStatus(response, responseContent, "granting calendar delegation to '%s'".formatted(delegatedToEmail));
            });
    }

    public Mono<Void> createCalendarCollection(String username, URI uri) {
        String mkcalendarBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <C:mkcalendar xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
              <D:set>
                <D:prop>
                  <D:displayname>Secondary Calendar</D:displayname>
                </D:prop>
              </D:set>
            </C:mkcalendar>
            """;

        return client.headers(headers -> headers
                .add(HttpHeaderNames.CONTENT_TYPE, "application/xml")
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username)))
            .request(HttpMethod.valueOf("MKCALENDAR"))
            .uri(uri.toString())
            .send(Mono.just(Unpooled.wrappedBuffer(mkcalendarBody.getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> {
                if (response.status().code() == 201) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar collection '{}' created successfully.", uri));
                }
                return DavClientHelper.unexpectedStatus(response, responseContent, "creating calendar collection '%s'".formatted(uri));
            });
    }

    public Mono<Void> createCalendar(String username, URI uri, Calendar calendarData) {
        return client.headers(headers -> headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username)))
            .request(HttpMethod.PUT)
            .uri(uri.toString())
            .send(Mono.just(Unpooled.wrappedBuffer(calendarData.toString().getBytes(StandardCharsets.UTF_8))))
            .responseSingle((response, responseContent) -> {
                if (response.status().code() == 201) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar object '{}' created successfully.", uri));
                }
                return DavClientHelper.unexpectedStatus(response, responseContent, "creating calendar object '%s'".formatted(uri));
            });
    }

    public Mono<Void> deleteCalendar(String username, URI uri) {
        return client.headers(headers -> headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain")
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username)))
            .request(HttpMethod.DELETE)
            .uri(uri.toString())
            .responseSingle((response, responseContent) -> {
                if (response.status().code() == 204) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("Calendar object '{}' has been deleted successfully.", uri));
                }
                return DavClientHelper.unexpectedStatus(response, responseContent, "deleting calendar object '%s'".formatted(uri));
            });
    }

    public Mono<Void> sendITIPRequest(String username, URI uri, byte[] json) {
        return client.headers(headers -> headers.add(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(username)))
            .request(HttpMethod.valueOf("ITIP"))
            .uri(uri.toString())
            .send(Mono.just(Unpooled.wrappedBuffer(json)))
            .responseSingle((response, responseContent) -> {
                if (response.status().code() == 204) {
                    return ReactorUtils.logAsMono(() -> LOGGER.info("Send itip request for '{}' successfully.", uri));
                }
                return DavClientHelper.unexpectedStatus(response, responseContent, "sending itip request for '%s'".formatted(uri));
            });
    }

    public Mono<DavCalendarObject> getCalendarObjectByUri(DavUser user, URI uri) {
        return client.headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(user.username())))
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
        return getCalendarObjects(user, eventUid).next();
    }

    public Flux<DavCalendarObject> getCalendarObjects(DavUser user, EventUid eventUid) {
        com.google.common.base.Preconditions.checkNotNull(user, "Dav user should not be null");

        return findUserCalendars(user)
            .flatMap(calendarURI -> getCalendarObjectContainingVEventFromSpecificCalendar(calendarURI, eventUid, user.username())
                .switchIfEmpty(ReactorUtils.logAsMono(() -> LOGGER.trace("VEvent '{}' was not found in Calendar '{}'.", eventUid, calendarURI))
                    .then(Mono.empty()))
                .onErrorResume(ex -> {
                    LOGGER.debug("Error while querying '{}' for VEvent '{}': ", calendarURI, eventUid, ex);
                    return Mono.empty();
                }));
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
                        .map(multistatus -> extractCalendarURIsFromResponse(multistatus, user.userId()));
                } else {
                    return Mono.error(new DavClientException(
                        String.format("Unexpected status code: %d when finding user calendars for user: %s",
                            response.status().code(), user.userId())));
                }
            })
            .flatMapMany(Flux::fromIterable);
    }

    private List<URI> extractCalendarURIsFromResponse(DavMultistatus multistatus, String userId) {
        return multistatus.getResponses().stream()
            .filter(DavResponse::isCalendarCollectionResponse)
            .flatMap(response -> response.getHref()
                .getValue()
                .filter(href -> href.startsWith(CALENDAR_PATH + userId + "/"))
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
                .add(HttpHeaderNames.AUTHORIZATION, config.authenticationToken(user.username())))
            .request(HttpMethod.POST)
            .uri(CALENDAR_PATH + "freebusy")
            .send(Mono.just(Unpooled.wrappedBuffer(request.serializeAsBytes())))
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.OK) {
                    return byteBufMono.asByteArray().map(FreeBusyResponse::deserialize);
                }
                return DavClientHelper.unexpectedStatus(response, byteBufMono, "querying freebusy for user '%s'".formatted(user.userId()));
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
