package com.linagora.tmail.james.common.calendar.getattendance

import java.util.Optional
import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.calendar.getattendance.CalDavLinagoraCalendarEventAttendanceGetMethodContract.CALMLY_AWAIT
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ALICE, ALICE_ACCOUNT_ID, ALICE_PASSWORD, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.{MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.SMTPMessageSender
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Test}

object CalDavLinagoraCalendarEventAttendanceGetMethodContract {
  private lazy val CALMLY_AWAIT: ConditionFactory = Awaitility.`with`
    .pollInterval(ONE_HUNDRED_MILLISECONDS)
    .and.`with`.pollDelay(ONE_HUNDRED_MILLISECONDS)
    .await
}

abstract class CalDavLinagoraCalendarEventAttendanceGetMethodContract extends LinagoraCalendarEventAttendanceGetMethodContract {

  @BeforeEach
  override def setUp(server: GuiceJamesServer): Unit = {
    super.setUp(server)
  }

  @Test
  def shouldFailWhenUserNotFoundInDavServer(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAliceInviteBobIcsAttachment.eml", icsPartId = "3")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

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
      given
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
           |  "CalendarEventAttendance/get",
           |  {
           |    "accountId": "$ANDRE_ACCOUNT_ID",
           |    "accepted": [],
           |    "rejected": [],
           |    "tentativelyAccepted": [],
           |    "needsAction": [],
           |    "notDone": {
           |      "$blobId": {
           |        "description": "Unable to find user in Dav server with username '${ANDRE.asString()}'",
           |        "type": "serverFail"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  override def _sendInvitationEmailToBobAndGetIcsBlobIds(server: GuiceJamesServer, invitationEml: String, icsPartIds: String*): Seq[String] = {
    def searchBobInboxForNewMessages(): Optional[MessageId] = {
      server.getProbe(classOf[MailboxProbeImpl])
        .searchMessage(
          MultimailboxesSearchQuery.from(
            SearchQuery.of(SearchQuery.all)).build, BOB.asString(), 1).stream().findAny()
    }

    val mail = ClassLoaderUtils.getSystemResourceAsString(invitationEml)

    new SMTPMessageSender(ALICE.getDomainPart.get().asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(ALICE.asString(), ALICE_PASSWORD)
      .sendMessageWithHeaders(ALICE.asString(), BOB.asString(), mail)

    CALMLY_AWAIT.atMost(10, TimeUnit.SECONDS)
      .dontCatchUncaughtExceptions()
      .until(() => searchBobInboxForNewMessages().isPresent)

    val messageId = searchBobInboxForNewMessages().get()

    icsPartIds.map(partId => s"${messageId.serialize()}_$partId")
  }
}