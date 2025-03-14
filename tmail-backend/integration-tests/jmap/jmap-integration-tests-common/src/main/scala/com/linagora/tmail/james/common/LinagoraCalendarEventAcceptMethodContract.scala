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

import java.util.concurrent.TimeUnit

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.common.LinagoraCalendarEventAcceptMethodContract.PASSWORD
import com.linagora.tmail.james.common.LinagoraCalendarEventMethodContractUtilities.sendDynamicInvitationEmailAndGetIcsBlobIds
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import play.api.libs.json.Json

object LinagoraCalendarEventAcceptMethodContract {
  val PASSWORD = "1"
}

abstract class LinagoraCalendarEventAcceptMethodContract {
  var senderOne: User = User("ALICE", ALICE.asString(), ALICE_PASSWORD)
  var senderTwo: User = User("CEDRIC", CEDRIC.asString(), PASSWORD)
  var receiver: User =  User("BOB", BOB.asString(), BOB_PASSWORD)
  var extraUser: User = User("ANDRE", ANDRE.asString(), ANDRE_PASSWORD)

  def randomBlobId: String

  def createUsers: java.util.List[User] =
    ImmutableList.of()

  @BeforeEach
  def setup(server: GuiceJamesServer): Unit = {
    val users = createUsers
    if (!users.isEmpty) {
      senderOne = users.get(0)
      senderTwo = users.get(1)
      receiver = users.get(2)
      extraUser = users.get(3)
    }

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(senderOne.username.getDomainPart.get().asString())
      .addDomain(senderTwo.username.getDomainPart.get().asString())
      .addDomain(receiver.username.getDomainPart.get().asString())
      .addDomain(extraUser.username.getDomainPart.get().asString())
      .addUser(senderOne.username.asString(), senderOne.password)
      .addUser(senderTwo.username.asString(), senderTwo.password)
      .addUser(receiver.username.asString(), receiver.password)
      .addUser(extraUser.username.asString(), extraUser.password)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(receiver.username, receiver.password)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(receiver.username))
  }

  @Test
  def acceptShouldSucceed(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$blobId" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": [ "$blobId" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def acceptAMissingMethodIcsShouldReturnNotAccept(server: GuiceJamesServer): Unit = {
    val missingMethodIcsBlobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithIcsMissingMethod.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$missingMethodIcsBlobId" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "notAccepted": {
           |            "$missingMethodIcsBlobId": {
           |                "type": "invalidPatch",
           |                "description": "The calendar must have REQUEST as a method"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def acceptAMissingOrganizerIcsShouldReturnNotAccept(server: GuiceJamesServer): Unit = {
    val missingOrganizerIcsBlobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithIcsMissingOrginizer.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$missingOrganizerIcsBlobId" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "notAccepted": {
           |            "$missingOrganizerIcsBlobId": {
           |                "type": "invalidPatch",
           |                "description": "Cannot extract the organizer from the calendar event."
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def acceptAMissingAttendeeIcsShouldReturnAccepted(server: GuiceJamesServer): Unit = {
    val missingAttendeeIcsBlobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithIcsMissingAttendee.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$missingAttendeeIcsBlobId" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": [ "$missingAttendeeIcsBlobId" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def acceptAMissingVEventIcsShouldReturnNotAccept(server: GuiceJamesServer): Unit = {
    val missingVEventIcsBlobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithIcsMissingVEVENT.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$missingVEventIcsBlobId" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "notAccepted": {
           |            "$missingVEventIcsBlobId": {
           |                "type": "invalidPatch",
           |                "description": "The calendar file must contain VEVENT component"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnNotFoundResultWhenBlobIdDoesNotExist(server: GuiceJamesServer): Unit = {
    val notFoundBlobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "88888")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$notFoundBlobId" ]
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
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "notFound": [ "$notFoundBlobId" ]
           |    }, "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnNotAcceptedWhenNotAnICS(server: GuiceJamesServer): Unit = {
    val notParsableBlobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "2")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$notParsableBlobId" ]
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
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "notAccepted": {
           |            "$notParsableBlobId": {
           |                "type": "invalidPatch",
           |                "description": "Error at line 1:Expected [BEGIN], read [The message has a text attachment.]"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldSucceedWhenMixSeveralCases(server: GuiceJamesServer): Unit = {
    val (notAcceptedId, notFoundBlobId, blobId) =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartIds = ("2", "99999", "3"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$notAcceptedId", "$blobId", "$notFoundBlobId" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": [ "$blobId" ],
           |        "notFound": [ "$notFoundBlobId" ],
           |        "notAccepted": {
           |            "$notAcceptedId": {
           |                "type": "invalidPatch",
           |                "description": "Error at line 1:Expected [BEGIN], read [The message has a text attachment.]"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnUnknownMethodWhenMissingOneCapability(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
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
  def shouldReturnUnknownMethodWhenMissingAllCapabilities(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |  "using": [],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
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
  def shouldFailWhenWrongAccountId(server: GuiceJamesServer): Unit = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
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
  def shouldNotFoundWhenDoesNotHavePermission(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${extraUser.accountId}",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`(buildRequestSpecification(server, extraUser))
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
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${extraUser.accountId}",
           |        "notFound": [ "$blobId" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldSucceedWhenDelegated(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(receiver.username, extraUser.username)

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$blobId" ]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response = `given`(buildRequestSpecification(server, extraUser))
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
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": [ "$blobId" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldFailWhenNumberOfBlobIdsTooLarge(server: GuiceJamesServer): Unit = {
    val blobIds: Array[String] = Range.inclusive(1, 999)
      .map(_ + "")
      .toArray
    val blogIdsJson: String = Json.stringify(Json.arr(blobIds)).replace("[[", "[").replace("]]", "]")
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds":  $blogIdsJson
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response: String = `given`
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
  def shouldNotCreatedWhenInvalidIcsPayload(server: GuiceJamesServer): Unit = {
    val (blobId1, blobId2) =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithTwoInvalidIcsAttachments.eml.mustache", senderOne, receiver, icsPartIds = ("5", "3"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$blobId1", "$blobId2" ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "notAccepted": {
           |            "$blobId2": {
           |                "type": "invalidPatch",
           |                "description": "Invalidate calendar event: STATUS : Value MUST match expression: (?i)TENTATIVE|CONFIRMED|CANCELLED|NEEDS-ACTION|COMPLETED|IN-PROCESS|CANCELLED|DRAFT|FINAL|CANCELLED"
           |            },
           |            "$blobId1": {
           |                "type": "invalidPatch",
           |                "description": "Invalidate calendar event: TRANSP : Value MUST match expression: (?i)OPAQUE|TRANSPARENT"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldFailWhenInvalidLanguage(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    `given`
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:calendar:event"],
               |  "methodCalls": [[
               |    "CalendarEvent/accept",
               |    {
               |      "accountId": "${receiver.accountId}",
               |      "blobIds": [ "$blobId" ],
               |      "language": "invalid"
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body("detail", Matchers.containsString("The language must be a valid ISO language code"))
  }

  @Test
  def shouldFailWhenUnsupportedLanguage(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    val response =  `given`
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:calendar:event"],
               |  "methodCalls": [[
               |    "CalendarEvent/accept",
               |    {
               |      "accountId": "${receiver.accountId}",
               |      "blobIds": [ "$blobId" ],
               |      "language": "vi"
               |    },
               |    "c1"]]
               |}""".stripMargin)
      .when
      .post
      .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "invalidArguments",
           |        "description": "The language only supports [en, fr]"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldSupportSpecialValidLanguages(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$blobId" ],
         |      "language": "en"
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": [ "$blobId" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldSendReplyMailToInvitor(server: GuiceJamesServer): Unit = {
    val senderInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(senderOne.username))

    val blobId = sendDynamicInvitationEmailAndGetIcsBlobIds(
      server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    `given`
      .body( s"""{
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "com:linagora:params:calendar:event"],
                |  "methodCalls": [[
                |    "CalendarEvent/accept",
                |    {
                |      "accountId": "${receiver.accountId}",
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
           |                "accountId": "${receiver.accountId}",
           |                "accepted": [ "$blobId" ]
           |            },
           |            "c1"
           |        ]
           |	]
           |}""".stripMargin))

    TimeUnit.SECONDS.sleep(1)

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: String =
        `given`(buildRequestSpecification(server, senderOne))
          .body( s"""{
                    |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    |    "methodCalls": [
                    |        [
                    |            "Email/query",
                    |            {
                    |                "accountId": "${senderOne.accountId}",
                    |                "filter": {
                    |                    "inMailbox": "${senderInboxId.serialize}"
                    |                }
                    |            },
                    |            "c1"
                    |        ],
                    |        [
                    |            "Email/get",
                    |            {
                    |                "accountId": "${senderOne.accountId}",
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
          .extract
          .body
          .asString

      assertThatJson(response)
        .inPath("methodResponses[1][1].list[0]")
        .isEqualTo(
          s"""{
             |    "subject": "ACCEPTED: Sprint planning #23 @ Wed Jan 11, 2017 (BOB <bob@domain.tld>)",
             |    "preview": "BOB <bob@domain.tld> has accepted this invitation.",
             |    "id": "$${json-unit.ignore}",
             |    "hasAttachment": true,
             |    "attachments": [
             |        {
             |            "charset": "UTF-8",
             |            "size": 874,
             |            "partId": "3",
             |            "blobId": "$${json-unit.ignore}",
             |            "type": "text/calendar"
             |        },
             |        {
             |            "charset": "us-ascii",
             |            "disposition": "attachment",
             |            "size": 874,
             |            "partId": "4",
             |            "blobId": "$${json-unit.ignore}",
             |            "name": "invite.ics",
             |            "type": "application/ics"
             |        }
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def mailReplyShouldSupportI18nWhenLanguageRequest(server: GuiceJamesServer): Unit = {
    val senderInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(senderOne.username))

    val blobId = sendDynamicInvitationEmailAndGetIcsBlobIds(
      server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")

    `given`
      .body( s"""{
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "com:linagora:params:calendar:event"],
                |  "methodCalls": [[
                |    "CalendarEvent/accept",
                |    {
                |      "accountId": "${receiver.accountId}",
                |      "blobIds": [ "$blobId" ],
                |      "language": "fr"
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
           |                "accountId": "${receiver.accountId}",
           |                "accepted": [ "$blobId" ]
           |            },
           |            "c1"
           |        ]
           |	]
           |}""".stripMargin))

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: String =
        `given`(buildRequestSpecification(server, senderOne))
          .body( s"""{
                    |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail" ],
                    |    "methodCalls": [
                    |        [
                    |            "Email/query",
                    |            {
                    |                "accountId": "${senderOne.accountId}",
                    |                "filter": {
                    |                    "inMailbox": "${senderInboxId.serialize}"
                    |                }
                    |            },
                    |            "c1"
                    |        ],
                    |        [
                    |            "Email/get",
                    |            {
                    |                "accountId": "${senderOne.accountId}",
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
          .extract
          .body
          .asString

      assertThatJson(response)
        .inPath("methodResponses[1][1].list[0]")
        .isEqualTo(
          s"""{
             |    "subject": "ACCEPTÉ: Sprint planning #23 @ Wed Jan 11, 2017 (BOB <bob@domain.tld>)",
             |    "preview": "BOB <bob@domain.tld> a accepté cette invitation.",
             |    "id": "$${json-unit.ignore}",
             |    "hasAttachment": true,
             |    "attachments": [
             |        {
             |            "charset": "UTF-8",
             |            "size": 874,
             |            "partId": "3",
             |            "blobId": "$${json-unit.ignore}",
             |            "type": "text/calendar"
             |        },
             |        {
             |            "charset": "us-ascii",
             |            "disposition": "attachment",
             |            "size": 874,
             |            "partId": "4",
             |            "blobId": "$${json-unit.ignore}",
             |            "name": "invite.ics",
             |            "type": "application/ics"
             |        }
             |    ]
             |}""".stripMargin)
    }
  }

  @Test
  def shouldNotFoundWhenBlobIdIsNotPrefixedByMessageId(server: GuiceJamesServer): Unit = {
    val blobId: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")
    val blobIdWithoutMessageId: String = "abcd123"

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": ["$blobIdWithoutMessageId", "$blobId"]
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
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": ["$blobId"],
           |        "notFound": ["$blobIdWithoutMessageId"]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldSucceedWhenCalendarBlobsComeFromDifferentMessages(server: GuiceJamesServer): Unit = {
    val blobIdFromMessage1: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderOne, receiver, icsPartId = "3")
    val blobIdFromMessage2: String =
      sendDynamicInvitationEmailAndGetIcsBlobIds(
        server, "template/emailWithAliceInviteBobIcsAttachment.eml.mustache", senderTwo, receiver, icsPartId = "3")

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:calendar:event"],
         |  "methodCalls": [[
         |    "CalendarEvent/accept",
         |    {
         |      "accountId": "${receiver.accountId}",
         |      "blobIds": [ "$blobIdFromMessage1", "$blobIdFromMessage2" ]
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
      .when(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "CalendarEvent/accept",
           |    {
           |        "accountId": "${receiver.accountId}",
           |        "accepted": [ "$blobIdFromMessage1", "$blobIdFromMessage2" ]
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  private def buildRequestSpecification(server: GuiceJamesServer, user: User): RequestSpecification =
    baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(user.username, user.password)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
}
