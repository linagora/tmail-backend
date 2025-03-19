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
import org.apache.james.jmap.routes.BlobResolvers;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.transport.mailets.ICALToHeader;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.dav.CalDavEventRepository;
import com.linagora.tmail.dav.DavCalendarObject;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.dav.DavUser;
import com.linagora.tmail.dav.EventUid;
import com.linagora.tmail.dav.OpenPaasDavUserProvider;
import com.linagora.tmail.james.jmap.CalendarEventNotFoundException;
import com.linagora.tmail.james.jmap.calendar.CalendarEventHelper;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.calendar.CalendarResolver;
import com.linagora.tmail.james.jmap.model.CalendarEventAttendanceResults;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;
import com.linagora.tmail.james.jmap.model.EventAttendanceStatusEntry;

import net.fortuna.ical4j.model.parameter.PartStat;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.jdk.javaapi.OptionConverters;

public class CalDavEventRepositoryTest {

    @RegisterExtension
    static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

    private static OpenPaasUser openPaasUser;

    private DavClient davClient;
    private CalDavEventRepository testee;
    private InMemoryIntegrationResources resources;
    private MailboxId mailboxId;
    private Username testUser;
    private MailboxSession testMailboxSession;

    @BeforeEach
    void setUp() throws Exception {
        davClient = new DavClient(dockerOpenPaasExtension.dockerOpenPaasSetup().davConfiguration());
        OpenPaasRestClient openPaasRestClient = new OpenPaasRestClient(dockerOpenPaasExtension.dockerOpenPaasSetup().openPaasConfiguration());

        resources = InMemoryIntegrationResources.defaultResources();
        testee = new CalDavEventRepository(davClient,
            resources.getMailboxManager().getSessionProvider(),
            resources.getMessageIdFactory(),
            resources.getMessageIdManager(),
            new OpenPaasDavUserProvider(openPaasRestClient),
            new CalendarResolver(new BlobResolvers(ImmutableSet.of())));

        setupNewTestUser();
    }

    private void setupNewTestUser() throws MailboxException {
        openPaasUser = dockerOpenPaasExtension.newTestUser();
        testUser = Username.of(openPaasUser.email());
        testMailboxSession = resources.getMailboxManager().createSystemSession(testUser);
        mailboxId = resources.getMailboxManager().createMailbox(MailboxPath.inbox(testUser),
            testMailboxSession).get();
    }

    private BlobId createNewMessageAndReturnFakeCalendarBlobId(String eventUid) {
        try {
            MessageManager.AppendResult messageAppendResult = resources.getMailboxManager().getMailbox(mailboxId, testMailboxSession)
                .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                    .setTo("bob@localhost.com")
                    .setBody("This is a message123", StandardCharsets.UTF_8)
                    .setField(new RawField(ICALToHeader.X_MEETING_UID_HEADER, eventUid))), testMailboxSession);
            MessageId messageId = messageAppendResult.getId().getMessageId();
            return BlobId.of(BlobId.of(messageId).get(), PartId.parse(new Random().nextInt(1_000_000) + "").get()).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        BlobId blobId = createNewMessageAndReturnFakeCalendarBlobId(calendarEvent.uid());

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
        BlobId blobId = createNewMessageAndReturnFakeCalendarBlobId(calendarEventB.uid());

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
        BlobId blobId = createNewMessageAndReturnFakeCalendarBlobId(calendarEventB.uid());

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then `isFree` should be true
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(OptionConverters.toJava(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().isFree()))
            .isEqualTo(Optional.of(true));
    }

    @Test
    void shouldReturnNotDoneWhenEventIdIsMissingInDavServer() {
        BlobId blobId = createNewMessageAndReturnFakeCalendarBlobId(UUID.randomUUID().toString());

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
        BlobId blobId = createNewMessageAndReturnFakeCalendarBlobId(eventUidB);

        CalendarEventAttendanceResults calendarEventAttendanceResults = Mono.from(testee.getAttendanceStatus(testUser, List.of(blobId))).block();

        // Then `isFree` should be true
        assertThat(calendarEventAttendanceResults).isNotNull();
        assertThat(calendarEventAttendanceResults.done().size()).isEqualTo(1);
        assertThat(OptionConverters.toJava(CollectionConverters.asJava(calendarEventAttendanceResults.done()).getFirst().isFree()))
            .isEqualTo(Optional.of(false));
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

        assertThatCode(() -> testee.rescheduledTiming(testUser, eventUidA, proposedStartDate, proposedEndDate).block())
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

        assertThatCode(() -> testee.rescheduledTiming(testUser, eventUidA, proposedStartDate, proposedEndDate).block())
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> testee.rescheduledTiming(testUser, eventUidA, proposedStartDate, proposedEndDate).block())
            .isInstanceOf(CalendarEventModifier.NoUpdateRequiredException.class);
    }

    @Test
    void rescheduledTimingShouldThrowWhenEventNotFound() {
        String nonExistentEventUid = UUID.randomUUID().toString();

        ZonedDateTime proposedStartDate = ZonedDateTime.now();
        ZonedDateTime proposedEndDate = proposedStartDate.plusHours(2);

        assertThatThrownBy(() -> testee.rescheduledTiming(testUser, nonExistentEventUid, proposedStartDate, proposedEndDate).block())
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

        assertThatThrownBy(() -> testee.rescheduledTiming(testUser, eventUidA, proposedStartDate, proposedEndDate).block())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot reschedule event");
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
}