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

package com.linagora.tmail.james.common

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

import com.linagora.tmail.james.common.LinagoraCalendarEventAttendanceGetMethodContract.bobAccountId
import com.linagora.tmail.james.jmap.calendar.CalendarEventHelper
import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.fortuna.ical4j.model.Calendar
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, _2_DOT_DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath, MessageId}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.{BeforeEach, Test}

trait CalendarEventCounterAcceptMethodContract {

  def bobCredential: UserCredential
  def aliceCredential: UserCredential

  def pushCalendarToDav(userCredential: UserCredential, eventUid: String, calendar: Calendar): Unit = {}

  def getCalendarFromDav(userCredential: UserCredential, eventUid: String): Calendar

  def randomMessageId: MessageId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(_2_DOT_DOMAIN.asString)
      .addDomain(bobCredential.username.getDomainPart.get().asString())
      .addUser(bobCredential.username.asString(), bobCredential.password)
      .addUser(aliceCredential.username.asString(), aliceCredential.password)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(bobCredential))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobCredential.username))
    bobAccountId = getAccountId(bobCredential, server)
  }

  @Test
  def shouldReturnAcceptedWhenValidRequest(server: GuiceJamesServer): Unit = {
    // Given: An original calendar event created and pushed to the server
    val eventUid: String = UUID.randomUUID().toString
    val originalStartDate: ZonedDateTime = ZonedDateTime.parse("2025-03-14T14:00:00Z")
    val originalEndDate: ZonedDateTime = originalStartDate.plusHours(2)
    val (_, originalCalendar: CalendarEventHelper) = createAndPushOriginalCalendarEvent(server, eventUid, originalStartDate, originalEndDate)

    // And: A counter event proposing a new schedule
    val counterEvent: Calendar = originalCalendar.generateCounterEvent(originalStartDate.minusDays(1), originalEndDate.minusDays(1))
    val counterEventBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid, counterEvent)

    // When: The counter event is accepted
    val response: String =
      `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$counterEventBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    // Then: The response should indicate the event was successfully accepted
    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventCounter/accept",
           |  {
           |    "accountId": "$bobAccountId",
           |    "accepted": [ "$counterEventBlobId" ]
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldUpdateCalendarOnDavServerWhenAccepted(server: GuiceJamesServer): Unit = {
    // Given: An original calendar event created and pushed to the server
    val eventUid: String = UUID.randomUUID().toString
    val originalStartDate: ZonedDateTime = ZonedDateTime.now()
    val originalEndDate: ZonedDateTime = originalStartDate.plusHours(2)
    val (_, originalCalendar: CalendarEventHelper) = createAndPushOriginalCalendarEvent(server, eventUid, originalStartDate, originalEndDate)

    // And: A counter event proposing a new schedule
    val counterProposedStartDate: ZonedDateTime = originalStartDate.minusDays(1)
    val counterProposedEndDate: ZonedDateTime = originalEndDate.minusDays(1)
    val counterEvent: Calendar = originalCalendar.generateCounterEvent(counterProposedStartDate, counterProposedEndDate)
    val counterEventBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid, counterEvent)

    // When: The counter event is accepted
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:calendar:event"],
           |  "methodCalls": [[
           |    "CalendarEventCounter/accept",
           |    {
           |      "accountId": "$bobAccountId",
           |      "blobIds": [ "$counterEventBlobId" ]
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].accepted", contains(counterEventBlobId))

    // Then: The calendar should be updated on the DAV server with the new proposed dates
    val parsedCalendar: CalendarEventParsed = CalendarEventParsed.from(getCalendarFromDav(bobCredential, eventUid)).head
    assertThat(parsedCalendar.startAsJava().get().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(counterProposedStartDate.truncatedTo(ChronoUnit.SECONDS))
    assertThat(parsedCalendar.endAsJava().get().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(counterProposedEndDate.truncatedTo(ChronoUnit.SECONDS))
  }

  @Test
  def shouldReturnNotAcceptedWhenEventMissingOnDavServer(server: GuiceJamesServer): Unit = {
    // Given: An event that does not exist on the DAV server
    val eventUid: String = UUID.randomUUID().toString
    val originalCalendar: CalendarEventHelper = CalendarEventHelper(
      uid = eventUid,
      start = ZonedDateTime.now(),
      end = ZonedDateTime.now().plusHours(1),
      attendee = aliceCredential.username.asString(),
      organizer = Some(bobCredential.username.asString()))

    // And: A counter event proposing a new schedule
    val counterEvent: Calendar = originalCalendar.generateCounterEvent(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1).minusDays(1))
    val counterEventBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid, counterEvent)

    // When: The counter event is accepted
    val response: String =
      `given`
        .body(
          s"""{
             |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$counterEventBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    // Then: The response should indicate that the event was not accepted due to missing on the DAV server
    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventCounter/accept",
           |  {
           |    "accountId": "$bobAccountId",
           |    "accepted": [],
           |    "notAccepted": {
           |      "$counterEventBlobId": {
           |        "type": "eventNotFound",
           |        "description": "The event you counter is not yet on your calendar"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }


  @Test
  def shouldReturnNotFoundWhenBlobIdDoesNotExists() : Unit = {
    val counterBlobId: String = randomMessageId.serialize()

    val response: String =
      `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$counterBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEventCounter/accept",
           |    {
           |        "accountId": "$bobAccountId",
           |        "accepted": [],
           |        "notFound": ["$counterBlobId"]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldHandleMixedAcceptanceScenariosCorrectly(server: GuiceJamesServer): Unit = {
    // Given: Multiple calendar events
    val eventUid: String = UUID.randomUUID().toString
    val originalStartDate: ZonedDateTime = ZonedDateTime.parse("2025-03-14T14:00:00Z")
    val originalEndDate: ZonedDateTime = originalStartDate.plusHours(2)
    val (_, originalCalendar: CalendarEventHelper) = createAndPushOriginalCalendarEvent(server, eventUid, originalStartDate, originalEndDate)

    // And: A counter event that will be accepted
    val acceptCounterEvent: Calendar = originalCalendar.generateCounterEvent(originalStartDate.minusDays(1), originalEndDate.minusDays(1))
    val acceptBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid, acceptCounterEvent)

    // And: A non-existent blob ID that should be marked as not found
    val nonExistentBlobId: String = randomMessageId.serialize()

    // And: A counter event that will be rejected
    val eventUid2: String = UUID.randomUUID().toString
    val notAcceptCounterEvent: Calendar = CalendarEventHelper(
      uid = eventUid2,
      start = ZonedDateTime.now(),
      end = ZonedDateTime.now().plusHours(1),
      attendee = aliceCredential.username.asString(),
      organizer = Some(bobCredential.username.asString())).generateCounterEvent(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1).minusDays(1))
    val notAcceptBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid2, notAcceptCounterEvent)

    // When: Attempting to accept all counter events
    val response: String =
      `given`
        .body(
          s"""{
             |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$acceptBlobId", "$notAcceptBlobId", "$nonExistentBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    // Then: The response should categorize events correctly
    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventCounter/accept",
           |  {
           |    "accountId": "$bobAccountId",
           |    "accepted": [ "$acceptBlobId" ],
           |    "notFound": [ "$nonExistentBlobId" ],
           |    "notAccepted": {
           |      "$notAcceptBlobId": {
           |        "type": "eventNotFound",
           |        "description": "The event you counter is not yet on your calendar"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnUnknownMethodWhenMissingOneCapability(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "CalendarEventCounter/accept",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |      "type": "unknownMethod",
           |      "description": "Missing capability(ies): com:linagora:params:calendar:event"
           |    },
           |    "c1"]""".stripMargin)
  }

  @Test
  def shouldReturnUnknownMethodWhenMissingAllCapabilities(): Unit = {
    val request: String =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "CalendarEventCounter/accept",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |      "type": "unknownMethod",
           |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:calendar:event"
           |    },
           |    "c1"]""".stripMargin)
  }

  @Test
  def shouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventCounter/accept",
         |    {
         |      "accountId": "unknownAccountId",
         |      "blobIds": [ "0f9f65ab-dc7b-4146-850f-6e4881093965" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "accountNotFound"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnNotFoundWhenDoesNotHavePermission(server: GuiceJamesServer): Unit = {
    // Given: A counter event uploaded by Alice
    val counterCalendar = CalendarEventHelper(
      uid = UUID.randomUUID().toString,
      start = ZonedDateTime.now(),
      end = ZonedDateTime.now().plusHours(1),
      attendee = aliceCredential.username.asString(),
      organizer = Some(bobCredential.username.asString())).generateCounterEvent(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusHours(1).minusDays(1))

    val aliceBlobId: String = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(aliceCredential))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build())
      .basePath("")
      .contentType("text/plain")
      .body(counterCalendar.toString.getBytes("UTF-8"))
    .when
      .post(s"/upload/${getAccountId(aliceCredential, server)}")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .path("blobId")

    // When: Bob tries to accept Alice's counter event without permission
    val response: String =
      `given`
        .body(
          s"""{
             |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$aliceBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    // Then: The response should indicate the event was not found due to insufficient permissions
    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventCounter/accept",
           |  {
           |    "accountId": "$bobAccountId",
           |    "accepted": [],
           |    "notFound": [ "$aliceBlobId" ]
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldAcceptEventWhenDelegatedUserHasPermission(server: GuiceJamesServer): Unit = {
    // Given: An original calendar event created and pushed to the server
    val eventUid: String = UUID.randomUUID().toString
    val originalStartDate: ZonedDateTime = ZonedDateTime.parse("2025-03-14T14:00:00Z")
    val originalEndDate: ZonedDateTime = originalStartDate.plusHours(2)
    val (_, originalCalendar: CalendarEventHelper) = createAndPushOriginalCalendarEvent(server, eventUid, originalStartDate, originalEndDate)

    // And: A counter event proposing a new schedule
    val counterEvent: Calendar = originalCalendar.generateCounterEvent(originalStartDate.minusDays(1), originalEndDate.minusDays(1))
    val counterEventBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid, counterEvent)

    // And: Alice is delegated to act on behalf of Bob
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(bobCredential.username, aliceCredential.username)

    // When: Alice accepts the counter event on behalf of Bob
    val response: String =
      `given`(baseRequestSpecBuilder(server)
        .setAuth(authScheme(aliceCredential))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .build())
        .basePath("/jmap")
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$counterEventBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    // Then: The response should indicate that the event was successfully accepted
    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventCounter/accept",
           |  {
           |    "accountId": "$bobAccountId",
           |    "accepted": [ "$counterEventBlobId" ]
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnNotUpdatedWhenInvalidIcsPayload(): Unit ={
    val uploadBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body("invalid")
    .when
      .post(s"/upload/$bobAccountId")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .path("blobId")

    val response: String =
      `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$uploadBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEventCounter/accept",
           |    {
           |        "accountId": "$bobAccountId",
           |        "accepted": [],
           |        "notAccepted": {
           |            "$uploadBlobId": {
           |                "type": "invalidArguments",
           |                "description": "The calendar file is not valid"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def counterAcceptRecurrenceEventShouldSucceed(server: GuiceJamesServer): Unit = {
    // Given: An original calendar event created and pushed to the server
    val eventUid: String = UUID.randomUUID().toString
    val originalCalendar: Calendar =
      s"""
         |BEGIN:VCALENDAR
         |VERSION:2.0
         |BEGIN:VEVENT
         |UID:$eventUid
         |DTSTART;TZID=Europe/Paris:20250328T090000
         |DTEND;TZID=Europe/Paris:20250328T100000
         |RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE
         |ORGANIZER;CN=John1 Doe1:${bobCredential.username.asString()}
         |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:${aliceCredential.username.asString()}
         |END:VEVENT
         |END:VCALENDAR""".stripMargin.asCalendar()

    pushCalendarToDav(bobCredential, eventUid, originalCalendar)

    val counterCalendarEvent: Calendar =
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |METHOD:COUNTER
         |BEGIN:VEVENT
         |UID:$eventUid
         |RECURRENCE-ID;TZID=Europe/Paris:20250409T090000
         |DTSTART;TZID=Europe/Paris:20250409T110000
         |DTEND;TZID=Europe/Paris:20250409T120000
         |ORGANIZER;CN=John1 Doe1:${bobCredential.username.asString()}
         |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto::${aliceCredential.username.asString()}
         |END:VEVENT
         |END:VCALENDAR""".stripMargin.asCalendar()

    val counterEventBlobId: String = createNewEmailWithCalendarAttachment(server, eventUid, counterCalendarEvent)

    // When: The counter event is accepted
    val response: String =
      `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventCounter/accept",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$counterEventBlobId" ]
             |    },
             |    "c1"]]
             |}""".stripMargin)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

    // Then : The response should indicate the event was successfully accepted
    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventCounter/accept",
           |  {
           |    "accountId": "$bobAccountId",
           |    "accepted": [ "$counterEventBlobId" ]
           |  },
           |  "c1"
           |]""".stripMargin)

    // Then: The calendar should be updated on the DAV server with the new proposed dates
    val calendarOnDav: Calendar = getCalendarFromDav(bobCredential, eventUid)

    assertThat(calendarOnDav.toString.removeDTSTAMPLines())
      .isEqualToNormalizingNewlines(
        s"""BEGIN:VCALENDAR
           |VERSION:2.0
           |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
           |BEGIN:VEVENT
           |UID:$eventUid
           |DTSTART;TZID=Europe/Paris:20250328T090000
           |DTEND;TZID=Europe/Paris:20250328T100000
           |RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE
           |ORGANIZER;CN=John1 Doe1:${bobCredential.username.asString()}
           |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:${aliceCredential.username.asString()}
           |END:VEVENT
           |BEGIN:VEVENT
           |UID:$eventUid
           |DTSTART;TZID=Europe/Paris:20250409T110000
           |DTEND;TZID=Europe/Paris:20250409T120000
           |ORGANIZER;CN=John1 Doe1:${bobCredential.username.asString()}
           |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:${aliceCredential.username.asString()}
           |RECURRENCE-ID;TZID=Europe/Paris:20250409T090000
           |SEQUENCE:1
           |END:VEVENT
           |END:VCALENDAR""".stripMargin)
  }

  private def createAndPushOriginalCalendarEvent(server: GuiceJamesServer,
                                                 eventUid: String,
                                                 originalStartDate: ZonedDateTime,
                                                 originalEndDate: ZonedDateTime): (String, CalendarEventHelper) = {
    val originalCalendar: CalendarEventHelper = CalendarEventHelper(uid = eventUid,
      start = originalStartDate,
      end = originalEndDate,
      attendee = aliceCredential.username.asString(),
      organizer = Some(bobCredential.username.asString()))

    val blobId: String = createNewEmailWithCalendarAttachment(server, eventUid, originalCalendar.asCalendar)
    pushCalendarToDav(bobCredential, eventUid, originalCalendar.asCalendar)
    (blobId, originalCalendar)
  }

  // return calendar messagePart blobId
  private def createNewEmailWithCalendarAttachment(server: GuiceJamesServer,
                                                   eventUid: String = UUID.randomUUID().toString,
                                                   calendar: Calendar): String = {

    val calendarAsBytes = calendar.toString.getBytes("UTF-8")

    // upload calendar attachment
    val uploadBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(calendarAsBytes)
    .when
      .post(s"/upload/$bobAccountId")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .path("blobId")

    // create email with calendar attachment
    val bobMailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, bobCredential.username.asString, DefaultMailboxes.INBOX)

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
           |  "methodCalls": [
           |    [
           |      "Email/set",
           |      {
           |        "accountId": "$bobAccountId",
           |        "create": {
           |          "aaaaaa": {
           |            "mailboxIds": {
           |              "${bobMailboxId.serialize}": true
           |            },
           |            "header:X-MEETING-UID:asText": "$eventUid",
           |            "subject": "World domination",
           |            "textBody": [ { "partId": "a49d", "type": "text/plain" } ],
           |            "bodyValues": {
           |              "a49d": {
           |                "value": "Calendar Description",
           |                "isTruncated": false,
           |                "isEncodingProblem": false
           |              }
           |            },
           |            "attachments": [
           |              {
           |                "blobId": "$uploadBlobId",
           |                "size": ${calendarAsBytes.length},
           |                "name": "invite.ics",
           |                "type": "text/calendar",
           |                "disposition": "attachment"
           |              }
           |            ]
           |          }
           |        }
           |      },
           |      "c1"
           |    ],
           |    [
           |      "Email/get",
           |      {
           |        "accountId": "$bobAccountId",
           |        "ids": [ "#aaaaaa" ],
           |        "properties": [
           |          "attachments"
           |        ]
           |      },
           |      "c2"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .path("methodResponses[1][1].list[0].attachments[0].blobId")
  }

  private def getAccountId(userCredential: UserCredential, server: GuiceJamesServer): String =
    `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(userCredential))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build())
      .get("/session")
    .`then`()
      .statusCode(200)
      .contentType(JSON)
      .extract
      .body
      .path("primaryAccounts[\"urn:ietf:params:jmap:core\"]")

  implicit class ImplicitCalendar(value: String) {
    def asCalendar(): Calendar =
      CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)))

    def removeDTSTAMPLines(): String =
      value.replaceAll("(?m)^DTSTAMP:.*\\R?", "").trim.stripMargin
  }

}
