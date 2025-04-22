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

import java.io.InputStream
import java.util.Optional
import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.LinagoraCalendarEventMethodContractUtilities.sendInvitationEmailToBobAndGetIcsBlobIds
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Test}

@Deprecated(forRemoval = true)
trait LinagoraCalendarEventReplyWithAMQPWorkflowContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
  }

  def randomBlobId: String

  def readAMQPContent: Optional[String]

  @Test
  def shouldPublishAMQPMessageWhenReplyAcceptSuccess(server: GuiceJamesServer): Unit = {
    // Given an invitation file
    val andreInboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))
    val blobId: String =
      sendInvitationEmailToBobAndGetIcsBlobIds(server, "emailWithAndreInviteBobIcsAttachment.eml", icsPartId = "3")

    // When Bob accepts the invitation
    `given`
      .body( s"""{
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
                |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |            "CalendarEvent/accept",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "accepted": [ "$blobId" ]
           |            },
           |            "c1"
           |        ]
           |	]
           |}""".stripMargin))

    // Confirm the reply event is in Andre's mailbox
    TimeUnit.SECONDS.sleep(1)

    awaitAtMostTenSeconds.untilAsserted { () =>
        `given`(buildAndreRequestSpecification(server))
          .body( s"""{
                    |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    |    "methodCalls": [
                    |        [
                    |            "Email/query",
                    |            {
                    |                "accountId": "$ANDRE_ACCOUNT_ID",
                    |                "filter": {
                    |                    "inMailbox": "${andreInboxId.serialize}"
                    |                }
                    |            },
                    |            "c1"
                    |        ],
                    |        [
                    |            "Email/get",
                    |            {
                    |                "accountId": "$ANDRE_ACCOUNT_ID",
                    |                "properties": [ "subject", "hasAttachment", "attachments", "preview" ],
                    |                "#ids": {
                    |                    "resultOf": "c1",
                    |                    "name": "Email/query",
                    |                    "path": "ids/*"
                    |                }
                    |            },
                    |            "c2"
                    |        ]
                    |    ]
                    |}""".stripMargin)
        .when
          .post
        .`then`
          .statusCode(SC_OK)
          .contentType(JSON)
          .body("methodResponses[1][1].list[0].preview", Matchers.equalTo("BOB <bob@domain.tld> has accepted this invitation."))
    }

    // Checking the AQMP message
    val aqmpContent: Optional[String] = readAMQPContent
    assertThat(aqmpContent).isPresent
    assertThatJson(aqmpContent.get())
      .withOptions(Option.IGNORING_EXTRA_FIELDS)
      .isEqualTo(
        s"""{
           |    "ical": "$${json-unit.ignore}",
           |    "sender": "bob@domain.tld",
           |    "recipient": "bob@domain.tld",
           |    "method": "REPLY"
           |}""".stripMargin)
  }

  private def buildAndreRequestSpecification(server: GuiceJamesServer): RequestSpecification =
    baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

  private def uploadAndGetBlobId(payload: InputStream): String =
    `given`
      .basePath("")
      .body(payload)
    .when
      .post(s"/upload/$ACCOUNT_ID")
      .`then`
      .statusCode(SC_CREATED)
      .extract
      .jsonPath()
      .get("blobId")

  private def generateInviteIcs(invitee: String, organizer: String): String =
    s"""BEGIN:VCALENDAR
       |CALSCALE:GREGORIAN
       |VERSION:2.0
       |PRODID:-//Linagora//TMail Calendar//EN
       |METHOD:REQUEST
       |CALSCALE:GREGORIAN
       |BEGIN:VEVENT
       |UID:8eae5147-f2df-4853-8fe0-c88678bc8b9f
       |TRANSP:OPAQUE
       |DTSTART;TZID=Europe/Paris:20240223T160000
       |DTEND;TZID=Europe/Paris:20240223T163000
       |CLASS:PUBLIC
       |SUMMARY:Simple event
       |ORGANIZER;CN=comptetest15.linagora@domain.tld:mailto:${organizer}
       |DTSTAMP:20240222T204008Z
       |SEQUENCE:0
       |ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=BOB;PARTSTAT=NEEDS-ACTION;X-OBM-ID=348:mailto:${invitee}
       |END:VEVENT
       |END:VCALENDAR
       |""".stripMargin

}
