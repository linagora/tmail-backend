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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.linagora.tmail.dav.DavClient.MAX_CALENDAR_OBJECT_UPDATE_RETRIES;
import static com.linagora.tmail.dav.DavServerExtension.ALICE;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_CALENDAR_1;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_CALENDAR_2;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_CALENDAR_OBJECT_1;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_CALENDAR_OBJECT_2;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_DAV_USER;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_ID;
import static com.linagora.tmail.dav.DavServerExtension.ALICE_VEVENT_1;
import static com.linagora.tmail.dav.DavServerExtension.createDelegatedBasicAuthenticationToken;
import static com.linagora.tmail.dav.DavServerExtension.propfind;
import static com.linagora.tmail.dav.DavServerExtension.report;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import javax.net.ssl.SSLException;

import org.apache.http.HttpStatus;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.http.Body;
import com.linagora.tmail.dav.cal.FreeBusyRequest;
import com.linagora.tmail.dav.cal.FreeBusyResponse;
import com.linagora.tmail.dav.request.CardDavCreationObjectRequest;
import com.linagora.tmail.dav.request.GetCalendarByEventIdRequestBody;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

import ezvcard.parameter.EmailType;

class DavClientTest {
    private static final String OPENPAAS_USER_NAME = "openpaasUserName1";
    private static final String OPENPAAS_USER_ID = "openpaasUserId1";
    private static final DavUser OPEN_PAAS_DAV_USER = new DavUser(OPENPAAS_USER_ID, OPENPAAS_USER_NAME);
    private static final UnaryOperator<DavCalendarObject> DUMMY_CALENDAR_OBJECT_UPDATER = calendarObject -> calendarObject;

    @RegisterExtension
    static DavServerExtension davServerExtension = new DavServerExtension();

    private DavClient client;

    @BeforeEach
    void setup() throws SSLException {
        System.setProperty("MIN_CALENDAR_OBJECT_UPDATE_RETRY_BACKOFF_IN_MILLS", "10");
        client = new DavClient(davServerExtension.getDavConfiguration());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("MIN_CALENDAR_OBJECT_UPDATE_RETRY_BACKOFF_IN_MILLS");
    }

    @Test
    void existsCollectedContactShouldReturnTrueWhenHTTPResponseIs200() {
        String collectedContactUid = UUID.randomUUID().toString();
        davServerExtension.setCollectedContactExists(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid, true);
        assertThat(client.existsCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid).block())
            .isTrue();
    }

    @Test
    void existsCollectedContactShouldReturnFalseWhenHTTPResponseIs404() {
        String collectedContactUid = UUID.randomUUID().toString();
        davServerExtension.setCollectedContactExists(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid, false);
        assertThat(client.existsCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid).block())
            .isFalse();
    }

    @Test
    void existsCollectedContactShouldReturnFalseWhenHTTPResponseIs500() {
        String collectedContactUid = UUID.randomUUID().toString();
        davServerExtension.stubFor(
            get("/addressbooks/%s/collected/%s.vcf".formatted(OPENPAAS_USER_ID, collectedContactUid))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(OPENPAAS_USER_NAME)))
                .willReturn(serverError()));

        assertThatThrownBy(() -> client.existsCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid).block())
            .isInstanceOf(DavClientException.class);
    }

    @Test
    void createCollectedContactShouldNotThrowWhenHTTPResponseIs201() throws Exception {
        String collectedContactUid = UUID.randomUUID().toString();
        davServerExtension.setCreateCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, collectedContactUid);

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            collectedContactUid,
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            new CardDavCreationObjectRequest.Email(
                List.of(EmailType.HOME),
                new MailAddress("anbach4@lina.com")));

        assertThatCode(() -> client.createCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, request).block())
            .doesNotThrowAnyException();
    }

    @Test
    void createCollectedContactShouldThrowWhenHTTPResponseIs404() throws Exception {
        String collectedContactUid = UUID.randomUUID().toString();

        davServerExtension.stubFor(
            put("/addressbooks/user1/collected/%s.vcf".formatted(collectedContactUid))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(OPENPAAS_USER_NAME)))
                .willReturn(notFound()));

        CardDavCreationObjectRequest request = new CardDavCreationObjectRequest(
            "4.0",
            collectedContactUid,
            Optional.of("An Bach4"),
            Optional.of(List.of("An", "Bach4")),
            new CardDavCreationObjectRequest.Email(
                List.of(EmailType.HOME),
                new MailAddress("anbach4@lina.com")));

        assertThatThrownBy(() -> client.createCollectedContact(OPENPAAS_USER_NAME, OPENPAAS_USER_ID, request).block())
            .isInstanceOf(DavClientException.class);
    }

    @Test
    void findUserCalendarsShouldSucceed() {
        assertThat(client.findUserCalendars(ALICE_DAV_USER).collectList().block())
            .hasSameElementsAs(
                List.of(
                    URI.create("/calendars/ALICE_ID/66e95872cf2c37001f0d2a09/"),
                    URI.create("/calendars/ALICE_ID/0b4e80d7-7337-458f-852d-7ae8d72a74b2/")));
    }

    @Test
    void findUserCalendarsShouldReturnEmptyWhenUserHasNoCalendars() {
        davServerExtension.stubFor(
            propfind("/calendars/" + OPENPAAS_USER_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(OPENPAAS_USER_NAME)))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(ClassLoaderUtils.getSystemResourceAsByteArray("EMPTY_MULTISTATUS_RESPONSE.xml")))
                        .withStatus(207)));

        assertThat(client.findUserCalendars(OPEN_PAAS_DAV_USER).collectList().block())
            .isEmpty();
    }

    @Test
    void findUserCalendarsShouldReturnEmptyWhenUserHasOnlySystemCalendars() {
        davServerExtension.stubFor(
            propfind("/calendars/" + OPENPAAS_USER_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(OPENPAAS_USER_NAME)))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(ClassLoaderUtils.getSystemResourceAsByteArray("CALENDARS_CONTAINING_ONLY_INBOX_OUTBOX.xml")))
                        .withStatus(207)));

        assertThat(client.findUserCalendars(OPEN_PAAS_DAV_USER).collectList().block())
            .isEmpty();
    }

    @Test
    void findUserCalendarsShouldFailWhenHTTPResponseIsNot207() {
        davServerExtension.stubFor(
            propfind("/calendars/" + OPENPAAS_USER_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(OPENPAAS_USER_NAME)))
                .willReturn(ok()));

        assertThatThrownBy(() -> client.findUserCalendars(OPEN_PAAS_DAV_USER).collectList().block())
            .isInstanceOf(DavClientException.class);
    }


    @Test
    void findUserCalendarsShouldNotFailWhenAnyOfUserCalendarsHrefsIsInvalid() {
        davServerExtension.stubFor(
            propfind("/calendars/" + OPENPAAS_USER_ID)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(OPENPAAS_USER_NAME)))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(ClassLoaderUtils.getSystemResourceAsByteArray("CALENDARS_WITH_ONE_INVALID_HREF.xml")))
                        .withStatus(207)));

        assertThat(client.findUserCalendars(OPEN_PAAS_DAV_USER).collectList().block())
            .hasSameElementsAs(
                List.of(URI.create("/calendars/ALICE_ID/0b4e80d7-7337-458f-852d-7ae8d72a74b2/")));
    }

    @Test
    void getCalendarObjectContainingVEventShouldSucceed() {
        assertThat(client.getCalendarObject(ALICE_DAV_USER, ALICE_VEVENT_1).map(DavCalendarObject::calendarData).block())
            .isEqualTo(CalendarEventParsed.parseICal4jCalendar(
                ClassLoaderUtils.getSystemResourceAsSharedStream("VCALENDAR1.ics")));
    }

    @Test
    void getCalendarObjectContainingVEventShouldSucceedWhenQueryingOneOfUserCalendarsFails() {
        davServerExtension.stubFor(
            report("/calendars/%s/%s/".formatted(ALICE_ID, ALICE_CALENDAR_2))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .withHeader("Depth", equalTo("1"))
                .withRequestBody(equalTo(
                    new GetCalendarByEventIdRequestBody(ALICE_VEVENT_1).value()))
                .willReturn(notFound()));

        assertThat(client.getCalendarObject(ALICE_DAV_USER, ALICE_VEVENT_1).map(DavCalendarObject::calendarData).block())
            .isEqualTo(CalendarEventParsed.parseICal4jCalendar(
                ClassLoaderUtils.getSystemResourceAsSharedStream("VCALENDAR1.ics")));
    }

    @Test
    void getVCalendarContainingVEventShouldSucceedWhenVEventNotFoundInAnyUserCalendar() {
        davServerExtension.stubFor(
            report("/calendars/%s/%s/".formatted(ALICE_ID, ALICE_CALENDAR_1))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .withHeader("Depth", equalTo("1"))
                .withRequestBody(equalTo(
                    new GetCalendarByEventIdRequestBody(ALICE_VEVENT_1).value()))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(
                                ClassLoaderUtils.getSystemResourceAsByteArray("EMPTY_MULTISTATUS_RESPONSE.xml")))
                        .withStatus(207)));

        davServerExtension.stubFor(
            report("/calendars/%s/%s/".formatted(ALICE_ID, ALICE_CALENDAR_2))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .withHeader("Depth", equalTo("1"))
                .withRequestBody(equalTo(
                    new GetCalendarByEventIdRequestBody(ALICE_VEVENT_1).value()))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body(
                                ClassLoaderUtils.getSystemResourceAsByteArray("EMPTY_MULTISTATUS_RESPONSE.xml")))
                        .withStatus(207)));

        assertThat(client.getCalendarObject(ALICE_DAV_USER, ALICE_VEVENT_1).map(DavCalendarObject::calendarData).block())
            .isEqualTo(null);
    }

    @Test
    void updateCalendarObjectShouldSucceed() {
        assertThatCode(() -> client.updateCalendarObject(ALICE_DAV_USER, URI.create(ALICE_CALENDAR_OBJECT_1), DUMMY_CALENDAR_OBJECT_UPDATER).block())
            .doesNotThrowAnyException();
    }

    @Test
    void updateCalendarObjectShouldFailsWhenHTTPStatusNot204() {
        assertThatThrownBy(() -> client.updateCalendarObject(ALICE_DAV_USER, URI.create(ALICE_CALENDAR_OBJECT_2), DUMMY_CALENDAR_OBJECT_UPDATER).block())
            .isInstanceOf(DavClientException.class);
    }

    @Test
    void updateCalendarObjectShouldRetryOnPreconditionFailedResponse412() {
        davServerExtension.stubFor(
            put(ALICE_CALENDAR_OBJECT_1)
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml"))
                .willReturn(aResponse().withStatus(HttpStatus.SC_PRECONDITION_FAILED)));

        assertThatThrownBy(() -> client.updateCalendarObject(ALICE_DAV_USER, URI.create(ALICE_CALENDAR_OBJECT_1), DUMMY_CALENDAR_OBJECT_UPDATER).block())
            .isInstanceOf(DavClientException.class);

        davServerExtension.verify(MAX_CALENDAR_OBJECT_UPDATE_RETRIES + 1,
            putRequestedFor(
                urlEqualTo(ALICE_CALENDAR_OBJECT_1))
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/xml")));
    }

    @Test
    void getPrincipalShouldSucceed() {
        assertThatCode(() -> client.getPrincipal(Username.of(ALICE)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void freeBusyQueryShouldSuccess() {
        davServerExtension.stubFor(
            post("/calendars/freebusy")
                .withHeader("Authorization", equalTo(createDelegatedBasicAuthenticationToken(ALICE)))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                    {
                        "start": "20250308T023500Z",
                        "end": "20250308T031500Z",
                        "users": [
                            "67c913533f46f500576ed03e"
                        ],
                        "uids": [
                            "b787cb16-fbe8-478f-8877-c699f9e314d8"
                        ]
                    }"""))
                .willReturn(
                    aResponse()
                        .withResponseBody(
                            new Body("""
                                {
                                    "start": "20250308T023500Z",
                                    "end": "20250308T031500Z",
                                    "users": [
                                        {
                                            "id": "67c913533f46f500576ed03e",
                                            "calendars": [
                                                {
                                                    "id": "67c913533f46f500576ed03e",
                                                    "busy": [
                                                        {
                                                            "uid": "2213afbb-d7c4-48fd-a7a4-919c56b745b0",
                                                            "start": "20250308T023000Z",
                                                            "end": "20250308T030000Z"
                                                        }
                                                    ]
                                                }
                                            ]
                                        }
                                    ]
                                }"""))
                        .withStatus(200)));

        FreeBusyRequest freeBusyRequest = FreeBusyRequest.builder()
            .start(Instant.parse("2025-03-08T02:35:00Z"))
            .end(Instant.parse("2025-03-08T03:15:00Z"))
            .user("67c913533f46f500576ed03e")
            .uid("b787cb16-fbe8-478f-8877-c699f9e314d8")
            .build();

        FreeBusyResponse freeBusyResponse = client.freeBusyQuery(ALICE_DAV_USER, freeBusyRequest).block();
        assertThat(freeBusyResponse.users().getFirst().calendars().getFirst().busy())
            .containsExactlyInAnyOrder( new FreeBusyResponse.BusyTime(
                "2213afbb-d7c4-48fd-a7a4-919c56b745b0",
                Instant.parse("2025-03-08T02:30:00Z"),
                Instant.parse("2025-03-08T03:00:00Z")));
    }
}
