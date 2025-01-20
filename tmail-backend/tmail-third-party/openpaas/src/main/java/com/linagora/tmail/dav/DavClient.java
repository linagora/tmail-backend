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
import java.util.function.Function;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.jetbrains.annotations.NotNull;
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
import com.linagora.tmail.james.jmap.model.CalendarAttendeeField;
import com.linagora.tmail.james.jmap.model.CalendarAttendeeParticipationStatus;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.CalendarParticipantsField;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.PartStat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import scala.compat.java8.OptionConverters;

public class DavClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(DavClient.class);

    private static final Duration RESPONSE_TIMEOUT_DEFAULT = Duration.ofSeconds(10);
    private static final String COLLECTED_ADDRESS_BOOK_PATH = "/addressbooks/%s/collected/%s.vcf";

    private final HttpClient client;
    private final DavConfiguration davConfiguration;
    private final Function<String, UsernamePasswordCredentials>
        adminCredentialWithDelegatedFunction;

    public DavClient(DavConfiguration davConfiguration) {
        this.davConfiguration = davConfiguration;
        this.client = createHttpClient(davConfiguration.trustAllSslCerts().orElse(false));

        this.adminCredentialWithDelegatedFunction = openPaasUsername -> new UsernamePasswordCredentials(davConfiguration.adminCredential().getUserName() + "&" + openPaasUsername,
            davConfiguration.adminCredential().getPassword());
    }

    private HttpClient createHttpClient(boolean trustAllSslCerts) {
        if (trustAllSslCerts) {
            return HttpClient.create()
                .baseUrl(davConfiguration.baseUrl().toString())
                .responseTimeout(davConfiguration.responseTimeout().orElse(RESPONSE_TIMEOUT_DEFAULT))
                .secure(sslContextSpec -> sslContextSpec.sslContext(
                    SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)));
        } else {
            return HttpClient.create()
                .baseUrl(davConfiguration.baseUrl().toString())
                .responseTimeout(davConfiguration.responseTimeout().orElse(RESPONSE_TIMEOUT_DEFAULT));
        }
    }

    public Mono<Boolean> existsCollectedContact(String username, String userId,
                                                String collectedId) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(userId), "OpenPaas user id should not be empty");
        Preconditions.checkArgument(StringUtils.isNotEmpty(collectedId), "Collected id should not be empty");

        return client
            .headers(headers -> headers.add(HttpHeaderNames.ACCEPT, "application/vcard+json"))
            .headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION, HttpUtils.createBasicAuthenticationToken(adminCredentialWithDelegatedFunction.apply(username))))
            .get()
            .uri(String.format(COLLECTED_ADDRESS_BOOK_PATH, userId, collectedId))
            .responseSingle((response, byteBufMono) -> switch (response.status().code()) {
                case 200 -> Mono.just(true);
                case 404 -> Mono.just(false);
                default ->
                    Mono.error(new DavClientException("Unexpected status code: " + response.status().code()
                                                      + " when checking contact exists for openPaasUserId: " + userId + " and collectedId: " + collectedId));
            });
    }

    public Mono<Void> createCollectedContact(String username, String userId,
                                             CardDavCreationObjectRequest creationObjectRequest) {
        return client
            .headers(headers -> headers.add(HttpHeaderNames.ACCEPT, "application/vcard+json"))
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
                    Mono.error(new DavClientException("Unexpected status code: " + response.status().code()
                                                      + " when creating contact for user: " + userId + " and collected id: " + creationObjectRequest.uid()));
            });
    }

    public Mono<PartStat> getUserParticipationStatus(String userId, String eventUid,
                                                     String userEmail) {
        return findAllUsersCalendars(userId)
            .flatMap(calendarURI ->
                getContainingVCalendar(eventUid, calendarURI)
                    .flatMapMany(vcalendar -> Flux.fromIterable(vcalendar.<VEvent>getComponents("VEVENT"))))
            .collectList()
            .flatMap(events -> doGetUserParticipationStatus(events, userEmail))
            .map(CalendarAttendeeParticipationStatus::value)
            .map(PartStat::new);
    }

    private Mono<CalendarAttendeeParticipationStatus> doGetUserParticipationStatus(List<VEvent> vEvents, String userEmail) {
        if (vEvents.size() == 1) {
            return Mono.just(vEvents.getFirst())
                .map(CalendarParticipantsField::from)
                .map(participantsField ->
                    participantsField.findParticipantByMailTo(userEmail)
                        .flatMap(CalendarAttendeeField::participationStatus))
                .map(OptionConverters::toJava)
                .flatMap(Mono::justOrEmpty)
                .doOnNext(System.out::println);
        } else {
            // TODO: handle recurring events
            return Mono.empty();
        }
    }

    private Mono<Calendar> getContainingVCalendar(String eventUid, URI calendarURI) {
        return client.headers(headers -> headers.add(HttpHeaderNames.ACCEPT, "application/xml"))
            .headers(headers -> headers.add("Depth", "1"))
            .headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION,
                HttpUtils.createBasicAuthenticationToken(
                    davConfiguration.adminCredential().getUserName(),
                    davConfiguration.adminCredential().getPassword())))
            .request(HttpMethod.valueOf("REPORT"))
            .uri(calendarURI.getPath())
            .send(Mono.just(Unpooled.wrappedBuffer(new GetCalendarByEventIdRequestBody(eventUid).value().getBytes())))
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.MULTI_STATUS) {
                    return byteBufMono.asString(StandardCharsets.UTF_8)
                        .flatMap(multiStatusResponse ->
                            extractVCalendarFromResponse(XMLUtil.parse(multiStatusResponse, DavMultistatus.class)));
                } else {
                    return Mono.error(
                        new DavClientException("Unexpected status code: " + response.status().code()
                                               + " when finding VCALENDAR object containing event: " + eventUid));
                }
            });
    }

    public Mono<Void> setUserParticipationStatus(String userId, String eventUuid,
                                                 PartStat partStat) {
        return Mono.empty();
    }

    private Flux<URI> findAllUsersCalendars(String userId) {
        return client.headers(headers -> headers.add(HttpHeaderNames.ACCEPT, "application/xml"))
            .headers(headers -> headers.add(HttpHeaderNames.AUTHORIZATION,
                HttpUtils.createBasicAuthenticationToken(
                    davConfiguration.adminCredential().getUserName(),
                    davConfiguration.adminCredential().getPassword())))
            .request(HttpMethod.valueOf("PROPFIND"))
            .uri(String.format("/calendars/%s", userId))
            .responseSingle((response, byteBufMono) -> {
                if (response.status() == HttpResponseStatus.MULTI_STATUS) {
                    return byteBufMono.asString(StandardCharsets.UTF_8)
                        .map(multiStatusResponse -> XMLUtil.parse(multiStatusResponse, DavMultistatus.class))
                        .map(this::extractCalendarURIsFromResponse);
                } else {
                    return Mono.error(
                        new DavClientException("Unexpected status code: " + response.status().code()
                                                      + " when finding user calendars for user: " + userId));
                }
            }).flatMapMany(Flux::fromIterable);
    }

    private static final Pattern CALENDAR_URI_PATTERN = Pattern.compile("/calendars/[^/]+/[^/]+/");

    private List<URI> extractCalendarURIsFromResponse(DavMultistatus multistatus) {
        List<URI> hrefs = new ArrayList<>();

        for (DavResponse response : multistatus.getResponses()) {
            response.getHref().getValue().ifPresent(href -> {
                if (CALENDAR_URI_PATTERN.matcher(href).matches()) {
                    if (!(href.endsWith("inbox/") || href.endsWith("outbox/"))) {
                        hrefs.add(URI.create(href));
                    }
                }
            });
        }

        LOGGER.trace("Found user calendars: {}", hrefs);

        return hrefs;
    }

    private static @NotNull Mono<Calendar> extractVCalendarFromResponse(DavMultistatus multistatus) {
        return Flux.fromIterable(multistatus.getResponses())
            .next()
            .map(davResponse -> davResponse.getPropstat().getProp().getCalendarData())
            .flatMap(Mono::justOrEmpty)
            .map(calendarData ->
                CalendarEventParsed.parseICal4jCalendar(
                    IOUtils.toInputStream(calendarData.getValue(), StandardCharsets.UTF_8)));
    }
}
