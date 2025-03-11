package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.LinagoraCalendarEventMethodContractUtilities.sendInvitationEmailToBobAndGetIcsBlobIds
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.Json

trait LinagoraCalendarEventAttendanceGetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(_2_DOT_DOMAIN.asString)
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ALICE.asString, ALICE_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
  }

  @Test
  def shouldReturnAccepted(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    acceptInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "accepted" } ]
            |  },
            |  "c1"
            |]""".stripMargin
    )
  }

  @Test
  def shouldReturnRejected(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    rejectInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "rejected" } ]
            |  },
            |  "c1"
            |]""".stripMargin
      )
  }

  @Test
  def shouldReturnMaybe(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    maybeInvitation(blobId)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
            |    "list": [ { "blobId": "$blobId", "eventAttendanceStatus": "tentativelyAccepted" } ]
            |  },
            |  "c1"
            |]""".stripMargin)
  }

  @Test
  def shouldReturnNeedsActionWhenNoEventAttendanceFlagAttachedToMail(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |      "accountId": "$ACCOUNT_ID",
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
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
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
            |    "accountId": "$ANDRE_ACCOUNT_ID",
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
         |      "accountId": "$ACCOUNT_ID",
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
         |      "accountId": "$ACCOUNT_ID",
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
    val acceptedEventBlobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")
    acceptInvitation(acceptedEventBlobId)

    val rejectedEventBlobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")
    rejectInvitation(rejectedEventBlobId)

    val rejectedEventBlobId2: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")
    rejectInvitation(rejectedEventBlobId2)

    val needsActionEventBlobId: String =
    sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    val notFoundBlobId = "99999_99999"

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEventAttendance/get",
         |    {
         |      "accountId": "$ACCOUNT_ID",
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""|[
            |  "CalendarEventAttendance/get",
            |  {
            |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(BOB, ANDRE)

    val blobId: String =
    sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    acceptInvitation(blobId)

    val bobAccountId = ACCOUNT_ID
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "CalendarEventAttendance/get",
           |  {
           |    "accountId": "$ACCOUNT_ID",
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

  private def acceptInvitation(blobId: String) = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "$ACCOUNT_ID",
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
         |      "accountId": "$ACCOUNT_ID",
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
         |      "accountId": "$ACCOUNT_ID",
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
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

}
