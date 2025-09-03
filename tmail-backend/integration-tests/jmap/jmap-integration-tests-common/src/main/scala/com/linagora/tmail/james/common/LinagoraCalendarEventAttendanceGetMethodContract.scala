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
import java.util.UUID

import com.linagora.tmail.james.common.LinagoraCalendarEventAttendanceGetMethodContract.bobAccountId
import com.linagora.tmail.james.jmap.calendar.CalendarEventHelper
import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.parameter.PartStat
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.{Assumptions, BeforeEach, Test}
import play.api.libs.json.Json

object LinagoraCalendarEventAttendanceGetMethodContract {
  var bobAccountId: String = _
}

trait LinagoraCalendarEventAttendanceGetMethodContract {

  def supportFreeBusyQuery: Boolean
  def bobCredential: UserCredential
  def aliceCredential: UserCredential
  def andreCredential: UserCredential

  def pushCalendarToDav(userCredential: UserCredential, calendar: Calendar, eventUid: String): Unit = {}

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(_2_DOT_DOMAIN.asString)
      .addDomain(bobCredential.username.getDomainPart.get().asString())
      .addUser(bobCredential.username.asString(), bobCredential.password)
      .addUser(aliceCredential.username.asString(), aliceCredential.password)
      .addUser(andreCredential.username.asString(), andreCredential.password)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(bobCredential))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(bobCredential.username))
    bobAccountId = getAccountId(bobCredential, server)
  }

  @Test
  def shouldReturnAccepted(server: GuiceJamesServer): Unit = {
    val blobId: String = createNewEmailWithCalendarAttachment(server)
    acceptInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$bobAccountId",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "accepted" } ]
            |  },
            |  "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldReturnIsFreeTrueWhenTimeSlotDoesNotConflicts(server: GuiceJamesServer): Unit = {
    Assumptions.assumeTrue(supportFreeBusyQuery, "This test is only relevant when freebusy is supported")
    val blobId: String = createNewEmailWithCalendarAttachment(server)

    val response =
      `given`
        .body(s"""{
                 |  "using": [
                 |    "urn:ietf:params:jmap:core",
                 |    "com:linagora:params:calendar:event"],
                 |  "methodCalls": [[
                 |    "CalendarEventAttendance/get",
                 |    {
                 |      "accountId": "$bobAccountId",
                 |      "blobIds": [ "$blobId" ]
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |    "CalendarEventAttendance/get",
            |    {
            |        "accountId": "$bobAccountId",
            |        "list": [
            |            {
            |                "blobId": "$blobId",
            |                "eventAttendanceStatus": "needsAction",
            |                "isFree": true
            |            }
            |        ]
            |    },
            |    "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldReturnIsFreeFalseWhenWhenTimeSlotConflicts(server: GuiceJamesServer): Unit = {
    Assumptions.assumeTrue(supportFreeBusyQuery, "This test is only relevant when freebusy is supported")

    // Given a calendar event A, and accepted it
    val startDateOfEventA: ZonedDateTime = ZonedDateTime.parse("2025-03-14T14:00:00Z")
    val endDateOfEventA: ZonedDateTime = startDateOfEventA.plusHours(2)
    val calendarEventA = new CalendarEventHelper(bobCredential.username.asString(), PartStat.NEEDS_ACTION, startDateOfEventA, endDateOfEventA)
    val blobIdA: String = createNewEmailWithCalendarAttachment(server, calendarEventA)
    acceptInvitation(blobIdA)

    // And a calendar event B has the conflict with time slot with event A
    val startDateOfEventB: ZonedDateTime = startDateOfEventA.plusHours(1)
    assertThat(startDateOfEventB.isBefore(endDateOfEventA)).isTrue
    val calendarEventB = new CalendarEventHelper(bobCredential.username.asString(), PartStat.NEEDS_ACTION, startDateOfEventB, startDateOfEventB.plusHours(1))
    val blobIdB: String = createNewEmailWithCalendarAttachment(server, calendarEventB)

    val response =
      `given`
        .body(s"""{
                 |  "using": [
                 |    "urn:ietf:params:jmap:core",
                 |    "com:linagora:params:calendar:event"],
                 |  "methodCalls": [[
                 |    "CalendarEventAttendance/get",
                 |    {
                 |      "accountId": "$bobAccountId",
                 |      "blobIds": [ "$blobIdB" ]
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |    "CalendarEventAttendance/get",
            |    {
            |        "accountId": "$bobAccountId",
            |        "list": [
            |            {
            |                "blobId": "$blobIdB",
            |                "eventAttendanceStatus": "needsAction",
            |                "isFree": false
            |            }
            |        ]
            |    },
            |    "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldReturnRejected(server: GuiceJamesServer): Unit = {
    val blobId: String = createNewEmailWithCalendarAttachment(server)

    rejectInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$bobAccountId",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "rejected" } ]
            |  },
            |  "c1"
            |]""".stripMargin
      )
  }

  @Test
  def shouldReturnMaybe(server: GuiceJamesServer): Unit = {
    val blobId: String = createNewEmailWithCalendarAttachment(server)

    maybeInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$bobAccountId",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "tentativelyAccepted" } ]
            |  },
            |  "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldReturnNeedsActionWhenNoEventAttendanceFlagAttachedToMail(server: GuiceJamesServer): Unit = {
    val blobId: String = createNewEmailWithCalendarAttachment(server)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$bobAccountId",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "needsAction" } ]
            |  },
            |  "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldFailWhenNumberOfBlobIdsTooLarge(): Unit = {
    val blobIds: List[String] = Range.inclusive(1, 999)
      .map(_ + "")
      .toList
    val blobIdsJson = blobIdsAsJson(blobIds)
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": $blobIdsJson
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String =
      `given`
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
           |        "type": "requestTooLarge",
           |        "description": "The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "unknownAccountId",
         |      "blobIds": [ "0f9f65ab-dc7b-4146-850f-6e4881093965" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
  def shouldNotFoundWhenDoesNotHavePermission(server: GuiceJamesServer): Unit = {
    val blobId: String = createNewEmailWithCalendarAttachment(server)

    val andreAccountId: String = getAccountId(andreCredential, server)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$andreAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`(buildAndreRequestSpecification(server))
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
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$andreAccountId",
            |    "list": [],
            |    "notFound": ["$blobId"]
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
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "123" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
  def shouldSucceedWhenMixSeveralCases(server: GuiceJamesServer): Unit = {
    val acceptedEventBlobId: String = createNewEmailWithCalendarAttachment(server)
    acceptInvitation(acceptedEventBlobId)

    val rejectedEventBlobId: String = createNewEmailWithCalendarAttachment(server)
    rejectInvitation(rejectedEventBlobId)

    val rejectedEventBlobId2: String = createNewEmailWithCalendarAttachment(server)
    rejectInvitation(rejectedEventBlobId2)

    val needsActionEventBlobId: String = createNewEmailWithCalendarAttachment(server)

    val notFoundBlobId = "99999_99999"

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$acceptedEventBlobId", "$rejectedEventBlobId", "$needsActionEventBlobId", "$rejectedEventBlobId2", "$notFoundBlobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`
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
      .when(Option.IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$bobAccountId",
            |    "list": [
            |      {
            |        "blobId": "$acceptedEventBlobId",
            |        "eventAttendanceStatus": "accepted"
            |      },
            |      {
            |        "blobId": "$rejectedEventBlobId",
            |        "eventAttendanceStatus": "rejected"
            |      },
            |      {
            |        "blobId": "$needsActionEventBlobId",
            |        "eventAttendanceStatus": "needsAction"
            |      },
            |      {
            |        "blobId": "$rejectedEventBlobId2",
            |        "eventAttendanceStatus": "rejected"
            |      }
            |    ],
            |    "notFound": [ "$notFoundBlobId" ]
            |  },
            |  "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldSucceedWhenDelegated(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(bobCredential.username, andreCredential.username)

    val blobId: String = createNewEmailWithCalendarAttachment(server)

    acceptInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =
      `given`(buildAndreRequestSpecification(server))
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
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventAttendance/get",
           |  {
           |    "accountId": "$bobAccountId",
           |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "accepted" } ]
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def getSessionShouldReturnRightVersionOfCalenderEventCapability(): Unit = {
    `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.\"com:linagora:params:calendar:event\".version", Matchers.is(2))
  }

  @Test
  def shouldReturnAcceptBaseEventAndRejectRecurrenceEvent(server: GuiceJamesServer): Unit = {
    // setup calendar
    val eventUid = UUID.randomUUID().toString
    val recurrenceId = "TZID=Europe/Paris:20250409T090000"
    val originalCalendarAsString =
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
         |BEGIN:VEVENT
         |UID:$eventUid
         |DTSTART;TZID=Europe/Paris:20250328T090000
         |DTEND;TZID=Europe/Paris:20250328T100000
         |RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=WE
         |ORGANIZER;CN=John1 Doe1;SCHEDULE-STATUS=3.7:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:${bobCredential.username.asString()}
         |END:VEVENT
         |BEGIN:VEVENT
         |UID:$eventUid
         |DTSTART;TZID=Europe/Paris:20250409T110000
         |DTEND;TZID=Europe/Paris:20250409T120000
         |ORGANIZER;CN=John1 Doe1:user_8f960db2-199e-42d2-97ac-65ddc344b96e@open-paas.org
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:${bobCredential.username.asString()}
         |DTSTAMP:20250331T083652Z
         |RECURRENCE-ID;$recurrenceId
         |SEQUENCE:1
         |END:VEVENT
         |END:VCALENDAR
         |""".stripMargin

    val originalCalendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(originalCalendarAsString.getBytes(StandardCharsets.UTF_8)))
    pushCalendarToDav(bobCredential, originalCalendar, eventUid)

    val requestCalendarWithRecurrenceId: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
         |CALSCALE:GREGORIAN
         |METHOD:REQUEST
         |BEGIN:VEVENT
         |UID:$eventUid
         |TRANSP:OPAQUE
         |DTSTART;TZID=Europe/Paris:20250409T110000
         |DTEND;TZID=Europe/Paris:20250409T120000
         |CLASS:PUBLIC
         |SUMMARY:Loop3
         |ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org
         |DTSTAMP:20250331T075231Z
         |RECURRENCE-ID;$recurrenceId
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI
         | DUAL;CN=John2 Doe2:mailto:${bobCredential.username.asString()}
         |SEQUENCE:1
         |END:VEVENT
         |END:VCALENDAR
         |""".stripMargin.getBytes(StandardCharsets.UTF_8)))

    val requestCalendarWithoutRecurrenceId: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
         |CALSCALE:GREGORIAN
         |METHOD:REQUEST
         |BEGIN:VEVENT
         |UID:$eventUid
         |TRANSP:OPAQUE
         |DTSTART;TZID=Asia/Jakarta:20250401T150000
         |DTEND;TZID=Asia/Jakarta:20250401T153000
         |CLASS:PUBLIC
         |SUMMARY:Loop3
         |ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org
         |DTSTAMP:20250331T075231Z
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI
         | DUAL;CN=John2 Doe2:mailto:${bobCredential.username.asString()}
         |SEQUENCE:1
         |END:VEVENT
         |END:VCALENDAR
         |""".stripMargin.getBytes(StandardCharsets.UTF_8)))

    val blobIdAccept: String = createNewEmailWithCalendarAttachment(server, requestCalendarWithoutRecurrenceId, eventUid, pushToDav = false)
    val blobIdReject: String = createNewEmailWithCalendarAttachment(server, requestCalendarWithRecurrenceId, eventUid, pushToDav = false)

    // accept base event invitation
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:calendar:event"],
           |  "methodCalls": [[
           |    "CalendarEvent/accept",
           |    {
           |      "accountId": "$bobAccountId",
           |      "blobIds": [ "$blobIdAccept" ]
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].accepted", hasItem(blobIdAccept))

    // reject recurrence event invitation
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:calendar:event"],
           |  "methodCalls": [[
           |    "CalendarEvent/reject",
           |    {
           |      "accountId": "$bobAccountId",
           |      "blobIds": [ "$blobIdReject" ]
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].rejected", hasItem(blobIdReject))

      // Then
      val response = `given`
        .body(
          s"""{
             |  "using": [
             |    "urn:ietf:params:jmap:core",
             |    "com:linagora:params:calendar:event"],
             |  "methodCalls": [[
             |    "CalendarEventAttendance/get",
             |    {
             |      "accountId": "$bobAccountId",
             |      "blobIds": [ "$blobIdAccept", "$blobIdReject" ]
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
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].list[*].isFree")
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "$bobAccountId",
            |    "list": [
            |      {
            |        "blobId": "$blobIdAccept",
            |        "eventAttendanceStatus": "accepted"
            |      },
            |      {
            |        "blobId": "$blobIdReject",
            |        "eventAttendanceStatus": "rejected"
            |      }
            |    ]
            |  },
            |  "c1"
            |]""".stripMargin)
  }

  private def acceptInvitation(blobId: String) = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def rejectInvitation(blobId: String) = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/reject",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def maybeInvitation(blobId: String) = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/maybe",
         |    {
         |      "accountId": "$bobAccountId",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
  }

  private def blobIdsAsJson(blobIds: List[String]) : String =
    Json.stringify(Json.arr(blobIds)).replace("[[", "[").replace("]]", "]")

  private def buildAndreRequestSpecification(server: GuiceJamesServer): RequestSpecification =
    baseRequestSpecBuilder(server)
      .setAuth(authScheme(andreCredential))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

  // return calendar messagePart blobId
  private def createNewEmailWithCalendarAttachment(server: GuiceJamesServer,
                                                   calendar: CalendarEventHelper = new CalendarEventHelper(
                                                     bobCredential.username.asString(),
                                                     PartStat.NEEDS_ACTION,
                                                     ZonedDateTime.now(),
                                                     ZonedDateTime.now().plusHours(1))): String = {
    createNewEmailWithCalendarAttachment(server, calendar.asCalendar, calendar.uid, supportFreeBusyQuery)
  }

  private def createNewEmailWithCalendarAttachment(server: GuiceJamesServer,
                                                   calendar: Calendar,
                                                   eventUid: String,
                                                   pushToDav: Boolean): String = {

    // upload calendar attachment
    val uploadBlobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .contentType("text/plain")
      .body(calendar.toString.getBytes(StandardCharsets.UTF_8))
    .when
      .post(s"/upload/$bobAccountId")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .path("blobId")

    // create email with calendar attachment
    val bobMailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(MailboxConstants.USER_NAMESPACE, bobCredential.username.asString, DefaultMailboxes.INBOX)

    val eventMessagePartBlobId: String = `given`
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
           |                "size": 808,
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

    if (pushToDav) {
      pushCalendarToDav(bobCredential, calendar, eventUid)
    }
    eventMessagePartBlobId
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
}
