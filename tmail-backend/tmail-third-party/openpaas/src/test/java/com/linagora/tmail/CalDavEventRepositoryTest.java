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

package com.linagora.tmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.jmap.mail.BlobId;
import org.apache.james.jmap.mail.PartId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.dav.CalDavEventRepository;
import com.linagora.tmail.dav.DavCalendarObject;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.dav.EventUid;
import com.linagora.tmail.dav.OpenPaasDavUserProvider;
import com.linagora.tmail.james.jmap.AttendanceStatus;
import com.linagora.tmail.james.jmap.CalendarEventNotFoundException;
import com.linagora.tmail.james.jmap.calendar.CalendarEventHelper;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.calendar.CalendarEventTimingUpdatePatch;
import com.linagora.tmail.james.jmap.calendar.CalendarEventUpdatePatch;
import com.linagora.tmail.james.jmap.calendar.CalendarResolver;
import com.linagora.tmail.james.jmap.calendar.OrganizerValidator;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.parameter.PartStat;
import reactor.core.publisher.Mono;
import reactor.core.scala.publisher.SMono;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class CalDavEventRepositoryTest {

    @RegisterExtension
    static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

    private static OpenPaasUser openPaasUser;
    private static OpenPaasUser aliceOpenPaasUser;

    private DavClient davClient;
    private CalDavEventRepository testee;
    private InMemoryIntegrationResources resources;
    private Username testUser;
    private CalendarResolver calendarResolver;

    @BeforeEach
    void setUp() throws Exception {
        davClient = new DavClient(dockerOpenPaasExtension.dockerOpenPaasSetup().davConfiguration());
        OpenPaasRestClient openPaasRestClient = new OpenPaasRestClient(dockerOpenPaasExtension.dockerOpenPaasSetup().openPaasConfiguration());

        resources = InMemoryIntegrationResources.defaultResources();
        calendarResolver = mock(CalendarResolver.class);
        when(calendarResolver.resolveRequestCalendar(any(), any(), any())).thenReturn(SMono.empty());
        testee = new CalDavEventRepository(davClient,
            resources.getMailboxManager().getSessionProvider(),
            new OpenPaasDavUserProvider(openPaasRestClient),
            calendarResolver);

        setupNewTestUser();
    }

    private void setupNewTestUser() {
        openPaasUser = dockerOpenPaasExtension.newTestUser();
        testUser = Username.of(openPaasUser.email());
        aliceOpenPaasUser = dockerOpenPaasExtension.newTestUser();
    }

    private BlobId setupCalendarResolver(String eventUid) {
        return setupCalendarResolver(eventUid, Optional.empty());
    }

    private BlobId setupCalendarResolver(String eventUid, Optional<String> recurrenceIdField) {
        String requestCalendar = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
            "METHOD:REQUEST\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUid + "\n" +
            "DTSTART;TZID=Europe/Paris:20250409T110000\n" +
            "DTEND;TZID=Europe/Paris:20250409T120000\n" +
            "ORGANIZER;CN=John1 Doe1:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250331T083652Z\n" + recurrenceIdField.map(rci -> rci + "\n").orElse("") +
            "SEQUENCE:1\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        Calendar calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(requestCalendar.getBytes(StandardCharsets.UTF_8)));

        when(calendarResolver.resolveRequestCalendar(any(), any(), any()))
            .thenReturn(SMono.just(calendar));

        return BlobId.of(new Random().nextInt(1_000_000) + "").get();
    }

    static Stream<PartStat> providePartStat() {
        return Stream.of(PartStat.ACCEPTED, PartStat.DECLINED, PartStat.TENTATIVE, PartStat.NEEDS_ACTION);
    }

    private void pushCalendarToDav(String username, CalendarEventHelper calendarEvent) {
        URI davCalendarUri = URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + calendarEvent.uid() + ".ics");
        davClient.createCalendar(username, davCalendarUri, calendarEvent.asCalendar()).block();
    }

    @ParameterizedTest
    @MethodSource("providePartStat")
    void getAttendanceStatusShouldReturnCorrectData(PartStat partStat) {
        // Given a calendar event
        ZonedDateTime startDate = ZonedDateTime.parse("2025-03-14T14:00:00Z");
        CalendarEventHelper calendarEvent = new CalendarEventHelper(openPaasUser.email(), partStat, startDate, startDate.plusHours(1));
        pushCalendarToDav(openPaasUser.email(), calendarEvent);

        // When getAttendanceStatus
        BlobId blobId = setupCalendarResolver(calendarEvent.uid());

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);

        EventAttendanceStatusEntry attendanceStatusEntry = CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst();

        SoftAssertions.assertSoftly(soflty -> {
            soflty.assertThat(attendanceStatusEntry.eventAttendanceStatus().toPartStat())
                .isEqualTo(partStat);
            soflty.assertThat(OptionConverters.toJava(attendanceStatusEntry.isFree()))
                .isEqualTo(Optional.of(true));
            soflty.assertThat(attendanceStatusEntry.blobId())
                .isEqualTo(blobId.value().toString());
        });
    }

    @Test
    void getAttendanceStatusShouldReturnRecurrenceEventStatusWhenRecurrenceIdIsPresent() {
        // Given a calendar event with recurrence id
        String eventUid = UUID.randomUUID().toString();
        String recurrenceIdValue = "RECURRENCE-ID:20250409T080000Z";
        String calendarAsString = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
            "CALSCALE:GREGORIAN\n" +
            "BEGIN:VTIMEZONE\n" +
            "TZID:Europe/Paris\n" +
            "BEGIN:STANDARD\n" +
            "TZOFFSETFROM:+0700\n" +
            "TZOFFSETTO:+0700\n" +
            "TZNAME:WIB\n" +
            "DTSTART:19700101T000000\n" +
            "END:STANDARD\n" +
            "END:VTIMEZONE\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUid + "\n" +
            "TRANSP:OPAQUE\n" +
            "DTSTART;TZID=Europe/Paris:20250401T150000\n" +
            "DTEND;TZID=Europe/Paris:20250401T153000\n" +
            "CLASS:PUBLIC\n" +
            "SUMMARY:Loop3\n" +
            "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI\n" +
            " DUAL;CN=John2 Doe2:mailto:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:u\n" +
            " ser1@open-paas.org\n" +
            "DTSTAMP:20250331T075231Z\n" +
            "SEQUENCE:0\n" +
            "END:VEVENT\n" +
            "BEGIN:VEVENT\n" +
            "UID:"+ eventUid + "\n" +
            "TRANSP:OPAQUE\n" +
            "DTSTART;TZID=Europe/Paris:20250409T170000\n" +
            "DTEND;TZID=Europe/Paris:20250409T173000\n" +
            "CLASS:PUBLIC\n" +
            "SUMMARY:Loop3\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org\n" +
            "DTSTAMP:20250331T075231Z\n" +
            recurrenceIdValue + "\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI\n" +
            " DUAL;CN=John2 Doe2:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL;CN=John1\n" +
            "  Doe1:mailto:user1@open-paas.org\n" +
            "SEQUENCE:1\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        // Push calendar to Dav
        URI davCalendarUri = URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUid + ".ics");
        davClient.createCalendar(openPaasUser.email(), davCalendarUri, stringAsCalendar(calendarAsString)).block();

        // Setup blobId for the event, with recurrence id
        BlobId blobId = setupCalendarResolver(eventUid, Optional.of(recurrenceIdValue));

        // When get AttendanceStatus
        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then it should return the correct data
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().eventAttendanceStatus())
            .isEqualTo(AttendanceStatus.NeedsAction);
    }

    @Test
    void getAttendanceStatusShouldReturnOriginalEventStatusWhenRecurrenceIdIsAbsent() {
        // Given a calendar event with recurrence id
        String eventUid = UUID.randomUUID().toString();
        String recurrenceIdValue = "RECURRENCE-ID:20250409T080000Z";
        String calendarAsString = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
            "CALSCALE:GREGORIAN\n" +
            "BEGIN:VTIMEZONE\n" +
            "TZID:Europe/Paris\n" +
            "BEGIN:STANDARD\n" +
            "TZOFFSETFROM:+0700\n" +
            "TZOFFSETTO:+0700\n" +
            "TZNAME:WIB\n" +
            "DTSTART:19700101T000000\n" +
            "END:STANDARD\n" +
            "END:VTIMEZONE\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUid + "\n" +
            "TRANSP:OPAQUE\n" +
            "DTSTART;TZID=Europe/Paris:20250401T150000\n" +
            "DTEND;TZID=Europe/Paris:20250401T153000\n" +
            "CLASS:PUBLIC\n" +
            "SUMMARY:Loop3\n" +
            "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI\n" +
            " DUAL;CN=John2 Doe2:mailto:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:u\n" +
            " ser1@open-paas.org\n" +
            "DTSTAMP:20250331T075231Z\n" +
            "SEQUENCE:0\n" +
            "END:VEVENT\n" +
            "BEGIN:VEVENT\n" +
            "UID:"+ eventUid + "\n" +
            "TRANSP:OPAQUE\n" +
            "DTSTART;TZID=Europe/Paris:20250409T170000\n" +
            "DTEND;TZID=Europe/Paris:20250409T173000\n" +
            "CLASS:PUBLIC\n" +
            "SUMMARY:Loop3\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org\n" +
            "DTSTAMP:20250331T075231Z\n" +
            recurrenceIdValue + "\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI\n" +
            " DUAL;CN=John2 Doe2:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL;CN=John1\n" +
            "  Doe1:mailto:user1@open-paas.org\n" +
            "SEQUENCE:1\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        // Push calendar to Dav
        URI davCalendarUri = URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUid + ".ics");
        davClient.createCalendar(openPaasUser.email(), davCalendarUri, stringAsCalendar(calendarAsString)).block();

        // Setup blobId for the event, with empty recurrence id
        BlobId blobId = setupCalendarResolver(eventUid, Optional.empty());

        // When get AttendanceStatus
        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then it should return the correct data
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().eventAttendanceStatus())
            .isEqualTo(AttendanceStatus.Accepted);
    }

    @Test
    void getAttendanceStatusShouldReturnIsFreeFalseWhenTimeSlotConflicts() {
        // Given a calendar event A, already ACCEPTED
        ZonedDateTime startDateOfEventA = ZonedDateTime.parse("2025-03-14T14:00:00Z");
        ZonedDateTime endDateOfEventA = startDateOfEventA.plusHours(2);
        CalendarEventHelper calendarEventA = new CalendarEventHelper(openPaasUser.email(), PartStat.ACCEPTED, startDateOfEventA, endDateOfEventA);
        pushCalendarToDav(openPaasUser.email(), calendarEventA);

        // And a calendar event B has the conflict with time slot with event A
        ZonedDateTime startDateOfEventB = startDateOfEventA.plusHours(1);
        assertThat(startDateOfEventB.isBefore(endDateOfEventA)).isTrue();
        CalendarEventHelper calendarEventB = new CalendarEventHelper(openPaasUser.email(), PartStat.NEEDS_ACTION, startDateOfEventB, startDateOfEventB.plusHours(1));
        pushCalendarToDav(openPaasUser.email(), calendarEventB);

        // When getAttendanceStatus of event B
        BlobId blobId = setupCalendarResolver(calendarEventB.uid());

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then `isFree` should be false
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(OptionConverters.toJava(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().isFree()))
            .isEqualTo(Optional.of(false));
    }

    @Test
    void getAttendanceStatusShouldReturnIsFreeTrueWhenTimeSlotDoesNotConflicts() {
        // Given a calendar event A, already ACCEPTED
        ZonedDateTime startDateOfEventA = ZonedDateTime.parse("2025-03-14T14:00:00Z");
        ZonedDateTime endDateOfEventA = startDateOfEventA.plusHours(2);
        CalendarEventHelper calendarEventA = new CalendarEventHelper(openPaasUser.email(), PartStat.ACCEPTED, startDateOfEventA, endDateOfEventA);
        pushCalendarToDav(openPaasUser.email(), calendarEventA);

        // And a calendar event B has not the conflict with time slot with event A
        ZonedDateTime startDateOfEventB = endDateOfEventA.plusHours(2);
        CalendarEventHelper calendarEventB = new CalendarEventHelper(openPaasUser.email(), PartStat.NEEDS_ACTION, startDateOfEventB, startDateOfEventB.plusHours(1));
        pushCalendarToDav(openPaasUser.email(), calendarEventB);

        // When getAttendanceStatus of event B
        BlobId blobId = setupCalendarResolver(calendarEventB.uid());

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then `isFree` should be true
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(OptionConverters.toJava(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().isFree()))
            .isEqualTo(Optional.of(true));
    }

    @Test
    void shouldReturnNotDoneWhenEventIdIsMissingInDavServer() {
        BlobId blobId = setupCalendarResolver(UUID.randomUUID().toString());

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        assertThat(calendarEventAttendanceResults).isNotNull();

        assertThat(OptionConverters.toJava(calendarEventAttendanceResults.notDone()))
            .isPresent()
            .satisfies(result -> assertThat(CollectionConverters.asJava(result.get().value()).keySet().stream()
                .map(Object::toString)
                .toList()).containsExactly(blobId.value().toString()));
    }

    @Test
    void shouldReturnNotFoundWhenMessagePartMismatch() {
        // Given not found blobId
        BlobId notFoundBlobId = BlobId.of(BlobId.of(resources.getMessageIdFactory().generate()).get(), PartId.parse(new Random().nextInt(1_000_000) + "").get()).get();
        // When
        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(notFoundBlobId))).block();
        // Then
        assertThat(calendarEventAttendanceResults).isNotNull();

        assertThat(OptionConverters.toJava(calendarEventAttendanceResults.notFound()))
            .isPresent()
            .satisfies(result -> assertThat(CollectionConverters.asJava(result.get().value()).stream()
                .map(Object::toString)
                .toList()).containsExactly(notFoundBlobId.value().toString()));
    }

    @Test
    void freeBusyQueryShouldRespectTimezoneOfCalendar() {
        // Given a calendar event A with Europe/Paris timezone, already ACCEPTED
        String eventUidA = UUID.randomUUID().toString();
        String calendarWithFrancesTimeZone = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250314T150000\n" +
            "DTEND;TZID=Europe/Paris:20250314T170000\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:user2@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:user2@open-paas.org\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarWithFrancesTimeZone.getBytes(StandardCharsets.UTF_8))))
            .block();

        String eventUidB = UUID.randomUUID().toString();
        /*
        DTSTART;TZID=Europe/Paris:20250314T150000
        21:00 Jakarta (+07:00) ➝ 15:00 Paris (+01:00)
        DTEND;TZID=Europe/Paris:20250314T170000:
        23:00 Jakarta (+07:00) ➝ 17:00 Paris (+01:00)
         */
        String calendarWithVietnamTimeZone = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidB + "\n" +
            "DTSTART;TZID=Asia/Jakarta:20250314T210000\n" +
            "DTEND;TZID=Asia/Jakarta:20250314T230000\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:user2@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:user2@open-paas.org\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidB + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarWithVietnamTimeZone.getBytes(StandardCharsets.UTF_8))))
            .block();

        // When getAttendanceStatus of event calendarWithVietnamTimeZone
        BlobId blobId = setupCalendarResolver(eventUidB);

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then `isFree` should be true
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(OptionConverters.toJava(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().isFree()))
            .isEqualTo(Optional.of(false));
    }

    @Test
    void updateEventOnOrganizerCalendarShouldImplicitlyUpdateAttendeeCalendar() {
        String eventUidA = UUID.randomUUID().toString();
        String calendarAsString = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
            "CALSCALE:GREGORIAN\n" +
            "BEGIN:VTIMEZONE\n" +
            "TZID:Europe/Paris\n" +
            "BEGIN:STANDARD\n" +
            "TZOFFSETFROM:+0700\n" +
            "TZOFFSETTO:+0700\n" +
            "TZNAME:WIB\n" +
            "DTSTART:19700101T000000\n" +
            "END:STANDARD\n" +
            "END:VTIMEZONE\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "TRANSP:OPAQUE\n" +
            "DTSTART;TZID=Europe/Paris:20250401T150000\n" +
            "DTEND;TZID=Europe/Paris:20250401T153000\n" +
            "CLASS:PUBLIC\n" +
            "SUMMARY:Loop3\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI\n" +
            " DUAL;CN=John2 Doe2:mailto:" + aliceOpenPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250331T075231Z\n" +
            "SEQUENCE:0\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        Calendar calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarAsString.getBytes(StandardCharsets.UTF_8)));

        // Create the event on organizer (OpenPaas user) calendar
        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                calendar)
            .block();
        // Create the event on attendee (Alice) calendar
        davClient.createCalendar(aliceOpenPaasUser.email(),
                URI.create("/calendars/" + aliceOpenPaasUser.id() + "/" + aliceOpenPaasUser.id() + "/" + eventUidA + ".ics"),
                calendar)
            .block();

        // Alice proposes a counter to OpenPaas user to modify the event start time
        String newStartTime = "20250320T160000";
        ZonedDateTime proposedStartDate = LocalDateTime.parse(newStartTime, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(ZoneId.of("Europe/Paris"));
        ZonedDateTime proposedEndDate = proposedStartDate.plusHours(2);
        String counterEvent = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "METHOD:COUNTER\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:" + proposedStartDate.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) + "\n" +
            "DTEND;TZID=Europe/Paris:" + proposedEndDate.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) + "\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2:mailto:" + aliceOpenPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250331T075231Z\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";
        CalendarEventModifier calendarEventModifier = CalendarEventModifier.of(
            CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(counterEvent.getBytes(StandardCharsets.UTF_8))), 
            testUser);

        // OpenPaas user accepts the counter proposal
        assertThatCode(() -> testee.updateEvent(Username.of(openPaasUser.email()), eventUidA, calendarEventModifier).block())
            .doesNotThrowAnyException();

        DavCalendarObject openPaasUserCalendar = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(eventUidA)).block();
        DavCalendarObject aliceDavCalendarObject = davClient.getCalendarObject(new DavUser(aliceOpenPaasUser.id(), aliceOpenPaasUser.email()), new EventUid(eventUidA)).block();

        // the event should be updated in both organizer (OpenPaas user) calendar and attendee (Alice) calendar
        assertThat(openPaasUserCalendar.uri()).isNotEqualTo(aliceDavCalendarObject.uri());
        assertThat(openPaasUserCalendar.calendarData().toString()).contains(newStartTime);
        assertThat(aliceDavCalendarObject.calendarData().toString()).contains(newStartTime);
    }

    @Test
    void rescheduledTimingShouldChangeEvent() {
        String eventUidA = UUID.randomUUID().toString();
        String calendarWithFrancesTimeZone = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250314T150000\n" +
            "DTEND;TZID=Europe/Paris:20250314T170000\n" +
            "ORGANIZER;CN=John1 Doe1:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarWithFrancesTimeZone.getBytes(StandardCharsets.UTF_8))))
            .block();

        ZonedDateTime proposedStartDate = LocalDateTime.parse("20250320T160000", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(ZoneId.of("Europe/Paris"));
        ZonedDateTime proposedEndDate = proposedStartDate.plusHours(2);

        assertThatCode(() -> testee.updateEvent(testUser, eventUidA, createRescheduledTimingModifier(proposedStartDate, proposedEndDate)).block())
            .doesNotThrowAnyException();

        DavCalendarObject davCalendarObject = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(eventUidA)).block();

        String updatedCalendar = davCalendarObject.calendarData().toString();
        assertThat(updatedCalendar).contains("DTSTART;TZID=Europe/Paris:20250320T160000");
        assertThat(updatedCalendar).contains("DTEND;TZID=Europe/Paris:20250320T180000");
    }

    @Test
    void rescheduledTimingShouldThrowWhenIdempotent() {
        String eventUidA = UUID.randomUUID().toString();
        String calendarWithFrancesTimeZone = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250314T150000\n" +
            "DTEND;TZID=Europe/Paris:20250314T170000\n" +
            "ORGANIZER;CN=John1 Doe1:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarWithFrancesTimeZone.getBytes(StandardCharsets.UTF_8))))
            .block();

        ZonedDateTime proposedStartDate = LocalDateTime.parse("20250320T160000", DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            .atZone(ZoneId.of("Europe/Paris"));
        ZonedDateTime proposedEndDate = proposedStartDate.plusHours(2);

        assertThatCode(() -> testee.updateEvent(testUser, eventUidA, createRescheduledTimingModifier(proposedStartDate, proposedEndDate)).block())
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> testee.updateEvent(testUser, eventUidA, createRescheduledTimingModifier(proposedStartDate, proposedEndDate)).block())
            .isInstanceOf(CalendarEventModifier.NoUpdateRequiredException.class);
    }

    @Test
    void rescheduledTimingShouldThrowWhenEventNotFound() {
        String nonExistentEventUid = UUID.randomUUID().toString();

        ZonedDateTime proposedStartDate = ZonedDateTime.now();
        ZonedDateTime proposedEndDate = proposedStartDate.plusHours(2);

        assertThatThrownBy(() -> testee.updateEvent(testUser, nonExistentEventUid, createRescheduledTimingModifier(proposedStartDate, proposedEndDate)).block())
            .isInstanceOf(CalendarEventNotFoundException.class)
            .hasMessageContaining("Calendar event not found");
    }

    @Test
    void rescheduledTimingShouldThrowWhenUserIsNotOrganizer() {
        String eventUidA = UUID.randomUUID().toString();
        String organizerEmail = UUID.randomUUID() + "@example.com";
        String calendarWithDifferentOrganizer = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250314T150000\n" +
            "DTEND;TZID=Europe/Paris:20250314T170000\n" +
            "ORGANIZER;CN=John1 Doe1:mailto:" + organizerEmail + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarWithDifferentOrganizer.getBytes(StandardCharsets.UTF_8))))
            .block();

        ZonedDateTime proposedStartDate = ZonedDateTime.now();
        ZonedDateTime proposedEndDate = proposedStartDate.plusHours(2);

        CalendarEventModifier eventModifier = new CalendarEventModifier(
            CollectionConverters.asScala(List.<CalendarEventUpdatePatch>of(new CalendarEventTimingUpdatePatch(proposedStartDate, proposedEndDate))).toSeq(),
            OptionConverters.toScala(Optional.empty()),
            new OrganizerValidator(testUser.asString()));

        assertThatThrownBy(() -> testee.updateEvent(testUser, eventUidA, eventModifier).block())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Can not update event");
    }

    @Test
    void updateRecurrenceEventShouldSuccess() {
        String eventUidA = UUID.randomUUID().toString();
        String calendarWithFrancesTimeZone = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250328T090000\n" +
            "DTEND;TZID=Europe/Paris:20250328T100000\n" +
            "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE\n" +
            "ORGANIZER;CN=John1 Doe1:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendarWithFrancesTimeZone.getBytes(StandardCharsets.UTF_8))))
            .block();

        String counterEvent = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "METHOD:COUNTER\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "RECURRENCE-ID;TZID=Europe/Paris:20250409T090000\n" +
            "DTSTART;TZID=Europe/Paris:20250409T110000\n" +
            "DTEND;TZID=Europe/Paris:20250409T120000\n" +
            "ORGANIZER;CN=John1 Doe1:" + openPaasUser.email() + "\n" +
            "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250313T113032\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        CalendarEventModifier calendarEventModifier = CalendarEventModifier.of(CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(counterEvent.getBytes(StandardCharsets.UTF_8))), testUser);

        assertThatCode(() ->  testee.updateEvent(testUser, eventUidA, calendarEventModifier).block())
            .doesNotThrowAnyException();

        DavCalendarObject davCalendarObject = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(eventUidA)).block();

        String updatedCalendar = davCalendarObject.calendarData().toString();
        assertThat(updatedCalendar.replaceAll("(?m)^DTSTAMP:.*\\R?", "").trim())
            .isEqualToNormalizingNewlines("BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + eventUidA + "\n" +
                "DTSTART;TZID=Europe/Paris:20250328T090000\n" +
                "DTEND;TZID=Europe/Paris:20250328T100000\n" +
                "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE\n" +
                "ORGANIZER;CN=John1 Doe1;SCHEDULE-STATUS=3.7:" + openPaasUser.email() + "\n" +
                "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
                "END:VEVENT\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + eventUidA + "\n" +
                "DTSTART;TZID=Europe/Paris:20250409T110000\n" +
                "DTEND;TZID=Europe/Paris:20250409T120000\n" +
                "ORGANIZER;CN=John1 Doe1:" + openPaasUser.email() + "\n" +
                "ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
                "RECURRENCE-ID;TZID=Europe/Paris:20250409T090000\n" +
                "SEQUENCE:1\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n".trim());
    }

    private CalendarEventModifier createRescheduledTimingModifier(ZonedDateTime proposedStartDate, ZonedDateTime proposedEndDate) {
        return CalendarEventModifier.of(new CalendarEventTimingUpdatePatch(proposedStartDate, proposedEndDate));
    }

    @Test
     void davClientShouldCreateNewCalendar() {
        ZonedDateTime startDateOfEventA = ZonedDateTime.parse("2025-03-14T14:00:00Z");
        ZonedDateTime endDateOfEventA = startDateOfEventA.plusHours(2);
        CalendarEventHelper calendarEventA = new CalendarEventHelper(openPaasUser.email(), PartStat.ACCEPTED, startDateOfEventA, endDateOfEventA);
        URI davCalendarUri = URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + calendarEventA.uid() + ".ics");
        davClient.createCalendar(openPaasUser.email(), davCalendarUri, calendarEventA.asCalendar()).block();

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(calendarEventA.uid())).block();
        assertThat(result).isNotNull();
    }

    @Test
    void davClientShouldDeleteCalendar() {
        ZonedDateTime startDateOfEventA = ZonedDateTime.parse("2025-03-14T14:00:00Z");
        ZonedDateTime endDateOfEventA = startDateOfEventA.plusHours(2);
        CalendarEventHelper calendarEventA = new CalendarEventHelper(openPaasUser.email(), PartStat.ACCEPTED, startDateOfEventA, endDateOfEventA);
        URI davCalendarUri = URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + calendarEventA.uid() + ".ics");
        davClient.createCalendar(openPaasUser.email(), davCalendarUri, calendarEventA.asCalendar()).block();
        davClient.deleteCalendar(openPaasUser.email(), davCalendarUri).block();

        DavCalendarObject result = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(calendarEventA.uid())).block();
        assertThat(result).isNull();
    }

    @Test
    void setAttendanceStatusShouldUpdateDavCalendar() {
        ZonedDateTime startDate = ZonedDateTime.parse("2025-03-14T14:00:00Z");
        CalendarEventHelper calendarEvent = new CalendarEventHelper(openPaasUser.email(), PartStat.NEEDS_ACTION, startDate, startDate.plusHours(1));
        pushCalendarToDav(openPaasUser.email(), calendarEvent);

        BlobId blobId = setupCalendarResolver(calendarEvent.uid(), Optional.empty());

        assertThatCode(() -> testee.setAttendanceStatus(testUser, AttendanceStatus.fromPartStat(PartStat.ACCEPTED).get(),
            blobId).block())
            .doesNotThrowAnyException();

        DavCalendarObject davCalendarObject = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(calendarEvent.uid())).block();

        assertThat(davCalendarObject.calendarData().toString())
            .contains("ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:user2@open-paas.org");
    }

    @Test
    void setAttendanceStatusShouldSupportRecurrenceEvent() {
        String eventUidA = UUID.randomUUID().toString();
        String calendar = "BEGIN:VCALENDAR\n" +
            "VERSION:2.0\n" +
            "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250328T090000\n" +
            "DTEND;TZID=Europe/Paris:20250328T100000\n" +
            "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE\n" +
            "ORGANIZER;CN=John1 Doe1;SCHEDULE-STATUS=3.7:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "END:VEVENT\n" +
            "BEGIN:VEVENT\n" +
            "UID:" + eventUidA + "\n" +
            "DTSTART;TZID=Europe/Paris:20250409T110000\n" +
            "DTEND;TZID=Europe/Paris:20250409T120000\n" +
            "ORGANIZER;CN=John1 Doe1:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org\n" +
            "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
            "DTSTAMP:20250331T083652Z\n" +
            "RECURRENCE-ID;TZID=Europe/Paris:20250409T090000\n" +
            "SEQUENCE:1\n" +
            "END:VEVENT\n" +
            "END:VCALENDAR\n";

        davClient.createCalendar(openPaasUser.email(),
                URI.create("/calendars/" + openPaasUser.id() + "/" + openPaasUser.id() + "/" + eventUidA + ".ics"),
                CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(calendar.getBytes(StandardCharsets.UTF_8))))
            .block();

        BlobId blobId = setupCalendarResolver(eventUidA, Optional.of("RECURRENCE-ID;TZID=Europe/Paris:20250409T090000"));

        assertThatCode(() -> testee.setAttendanceStatus(testUser,
            AttendanceStatus.Accepted, blobId).block()).doesNotThrowAnyException();

        DavCalendarObject davCalendarObject = davClient.getCalendarObject(new DavUser(openPaasUser.id(), openPaasUser.email()), new EventUid(eventUidA)).block();
        String updatedCalendar = davCalendarObject.calendarData().toString();
        assertThat(updatedCalendar.replaceAll("(?m)^DTSTAMP:.*\\R?", "").trim())
            .isEqualToNormalizingNewlines("BEGIN:VCALENDAR\n" +
                "VERSION:2.0\n" +
                "PRODID:-//Sabre//Sabre VObject 4.1.3//EN\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + eventUidA + "\n" +
                "DTSTART;TZID=Europe/Paris:20250328T090000\n" +
                "DTEND;TZID=Europe/Paris:20250328T100000\n" +
                "RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE\n" +
                "ORGANIZER;CN=John1 Doe1;SCHEDULE-STATUS=3.7:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org\n" +
                "ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:" + openPaasUser.email() + "\n" +
                "END:VEVENT\n" +
                "BEGIN:VEVENT\n" +
                "UID:" + eventUidA + "\n" +
                "DTSTART;TZID=Europe/Paris:20250409T110000\n" +
                "DTEND;TZID=Europe/Paris:20250409T120000\n" +
                "ORGANIZER;CN=John1 Doe1:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org\n" +
                "RECURRENCE-ID;TZID=Europe/Paris:20250409T090000\n" +
                "SEQUENCE:2\n" +
                "ATTENDEE;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2;PARTSTAT=ACCEPTED:mailto:" + openPaasUser.email() + "\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR\n".trim());
    }

    private Calendar stringAsCalendar(String input) {
        return CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
    }
}
