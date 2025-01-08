package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.LinagoraCalendarEventMethodContractUtilities.sendInvitationEmailToBobAndGetIcsBlobIds
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait LinagoraCalendarEventAttendanceGetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(_2_DOT_DOMAIN.asString)
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ALICE.asString, ALICE_PASSWORD)

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
            |    "accepted": ["$blobId"],
            |    "rejected": [],
            |    "tentativelyAccepted": [],
            |    "needsAction": []
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
            |    "accepted": [],
            |    "rejected": ["$blobId"],
            |    "tentativelyAccepted": [],
            |    "needsAction": []
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
            |    "accepted": [],
            |    "rejected": [],
            |    "tentativelyAccepted": ["$blobId"],
            |    "needsAction": []
            |  },
            |  "c1"
            |]""".stripMargin
      )
  }

  @Test
  def shouldReturnNeedsActionByDefault(server: GuiceJamesServer): Unit = {
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
            |    "accepted": [],
            |    "rejected": [],
            |    "tentativelyAccepted": [],
            |    "needsAction": ["$blobId"]
            |  },
            |  "c1"
            |]""".stripMargin
      )
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

}
