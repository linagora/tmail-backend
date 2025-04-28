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

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.MailboxClearContract.{BIG_LIMIT, MESSAGE, andreBaseRequest, andreInboxId, andreTrashId, bobBaseRequest, bobInboxId, bobTrashId}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCOUNT_ID => BOB_ACCOUNT_ID, _}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.{await => awaitility}
import org.awaitility.core.ConditionFactory
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Test}

object MailboxClearContract {
  var bobBaseRequest: RequestSpecification = _
  var andreBaseRequest: RequestSpecification = _
  var andreInboxId: MailboxId = _
  var andreTrashId: MailboxId = _
  var bobInboxId: MailboxId = _
  var bobTrashId: MailboxId = _

  val MESSAGE: Message = Message.Builder.of
    .setSubject("test")
    .setSender(BOB.asString)
    .setFrom(BOB.asString)
    .setTo(BOB.asString)
    .setBody("test mail", StandardCharsets.UTF_8)
    .build

  val BIG_LIMIT: Long = 10000L
}

trait MailboxClearContract {
  private lazy val await: ConditionFactory = awaitility.atMost(30, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    andreInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    andreTrashId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(ANDRE, "Trash"))

    bobInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    bobTrashId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.forUser(BOB, "Trash"))

    bobBaseRequest = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    andreBaseRequest = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def shouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "urn:ietf:params:jmap:mail",
         |        "com:linagora:params:jmap:mailbox:clear"
         |    ],
         |    "methodCalls": [
         |        [
         |            "Mailbox/clear",
         |            {
         |                "accountId": "unknownAccountId",
         |                "mailboxId": "$bobTrashId"
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin

    val response = `given`(bobBaseRequest)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
  def shouldFailWhenMissingMailboxClearCapability(): Unit = {
    val response: String = `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "mailboxId123"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "unknownMethod",
           |        "description": "Missing capability(ies): com:linagora:params:jmap:mailbox:clear"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldClearAllMessagesInTargetMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    mailboxProbe.appendMessage(BOB.asString(), MailboxPath.forUser(BOB, "Trash"), AppendCommand.from(MESSAGE))
    mailboxProbe.appendMessage(BOB.asString(), MailboxPath.forUser(BOB, "Trash"), AppendCommand.from(MESSAGE))

    val response: String = `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail",
           |        "com:linagora:params:jmap:mailbox:clear"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "${bobTrashId.serialize()}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "Mailbox/clear",
           |    {
           |        "accountId": "$BOB_ACCOUNT_ID"
           |    },
           |    "c1"
           |]""".stripMargin)

    await.untilAsserted(() => {
      val bobTrashMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
        .inMailboxes(bobTrashId).build,
        BOB.asString(),
        BIG_LIMIT)
      assertThat(bobTrashMessages).isEmpty()
    })
  }

  @Test
  def shouldFailWhenInvalidMailboxId(): Unit = {
    `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail",
           |        "com:linagora:params:jmap:mailbox:clear"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "invalidMailboxId"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].notCleared.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCleared.description", Matchers.is(s"For input string: \"invalidMailboxId\""))
  }

  @Test
  def shouldBeIdempotent(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val response: String = `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail",
           |        "com:linagora:params:jmap:mailbox:clear"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "${bobTrashId.serialize()}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "Mailbox/clear",
           |    {
           |        "accountId": "$BOB_ACCOUNT_ID"
           |    },
           |    "c1"
           |]""".stripMargin)

    val bobTrashMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
      .inMailboxes(bobTrashId).build,
      BOB.asString(),
      BIG_LIMIT)

    assertThat(bobTrashMessages).isEmpty()
  }

  @Test
  def shouldNotClearOtherMailboxesOfTheUser(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val bobInboxMessageId: MessageId = mailboxProbe
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(MESSAGE))
      .getMessageId
    mailboxProbe
      .appendMessage(BOB.asString(), MailboxPath.forUser(BOB, "Trash"), AppendCommand.from(MESSAGE))
      .getMessageId

    // clean Bob Trash
    val response: String = `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail",
           |        "com:linagora:params:jmap:mailbox:clear"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "${bobTrashId.serialize()}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "Mailbox/clear",
           |    {
           |        "accountId": "$BOB_ACCOUNT_ID"
           |    },
           |    "c1"
           |]""".stripMargin)

    // Should not clear Bob INBOX
    await.untilAsserted(() => {
      val bobInboxMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
        .inMailboxes(bobInboxId).build,
        BOB.asString(),
        BIG_LIMIT)
      val bobTrashMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
        .inMailboxes(bobTrashId).build,
        BOB.asString(),
        BIG_LIMIT)

      assertThat(bobTrashMessages).isEmpty()
      assertThat(bobInboxMessages).containsExactly(bobInboxMessageId)
    })
  }

  @Test
  def bobClearHistTrashShouldNotClearMailboxesOfOtherUser(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    mailboxProbe
      .appendMessage(BOB.asString(), MailboxPath.forUser(BOB, "Trash"), AppendCommand.from(MESSAGE))
      .getMessageId
    val andreTrashMessageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), MailboxPath.forUser(ANDRE, "Trash"), AppendCommand.from(MESSAGE))
      .getMessageId

    // clean Bob Trash
    val response: String = `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail",
           |        "com:linagora:params:jmap:mailbox:clear"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "${bobTrashId.serialize()}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "Mailbox/clear",
           |    {
           |        "accountId": "$BOB_ACCOUNT_ID"
           |    },
           |    "c1"
           |]""".stripMargin)

    // Should not clear Andre mailbox
    await.untilAsserted(() => {
      val bobTrashMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
        .inMailboxes(bobTrashId).build,
        BOB.asString(),
        BIG_LIMIT)
      val andreTrashMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
        .inMailboxes(andreTrashId).build,
        ANDRE.asString(),
        BIG_LIMIT)
      assertThat(bobTrashMessages).isEmpty()
      assertThat(andreTrashMessages).containsExactly(andreTrashMessageId)
    })
  }

  @Test
  def bobShouldNotBeAbleToClearAndreMailbox(server: GuiceJamesServer): Unit = {
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])

    val andreTrashMessageId: MessageId = mailboxProbe
      .appendMessage(ANDRE.asString(), MailboxPath.forUser(ANDRE, "Trash"), AppendCommand.from(MESSAGE))
      .getMessageId

    // Bob tries to clean Andre Trash
    val response: String = `given`(bobBaseRequest)
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:mail",
           |        "com:linagora:params:jmap:mailbox:clear"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Mailbox/clear",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "mailboxId": "${andreTrashId.serialize()}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "Mailbox/clear",
           |    {
           |        "accountId": "$BOB_ACCOUNT_ID",
           |        "notCleared": {
           |            "type": "notFound",
           |            "description": "${andreTrashId.serialize()} can not be found"
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)

    // Andre Trash should not be cleared
    await.untilAsserted(() => {
      val andreTrashMessages = mailboxProbe.searchMessage(MultimailboxesSearchQuery.from(SearchQuery.matchAll)
        .inMailboxes(andreTrashId).build,
        ANDRE.asString(),
        BIG_LIMIT)
      assertThat(andreTrashMessages).containsExactly(andreTrashMessageId)
    })
  }

}