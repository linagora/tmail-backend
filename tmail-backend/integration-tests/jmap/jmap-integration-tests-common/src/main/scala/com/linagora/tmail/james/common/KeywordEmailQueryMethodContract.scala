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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.{Date, Optional}

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.mail.Flags
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}

trait KeywordEmailQueryMethodContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  private lazy val UTC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def hasKeywordSortedByReceivedAtShouldReturnOnlyMailsWithThisCustomKeyword(server: GuiceJamesServer): Unit = {
    val message: Message = buildTestMessage
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val messageId = sendMessageToBobInbox(server, message, Optional.empty(), new Flags("custom"))

    sendMessageToBobInbox(server, message, Optional.empty(), new Flags())

    val request =
      """{
        |  "using": [
        |    "urn:ietf:params:jmap:core",
        |    "urn:ietf:params:jmap:mail"],
        |  "methodCalls": [[
        |    "Email/query",
        |    {
        |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |      "filter" : {
        |        "hasKeyword": "custom"
        |      },
        |      "sort": [{
        |        "property":"receivedAt",
        |        "isAscending": false
        |      }]
        |    },
        |    "c1"]]
        |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId.serialize}"]""")
    }
  }

  @Test
  def hasKeywordSortedByReceivedAtShouldYieldExpectedResults(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate2), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate1), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate2), new Flags("custom"))

    val request =
      """{
        |  "using": [
        |    "urn:ietf:params:jmap:core",
        |    "urn:ietf:params:jmap:mail"],
        |  "methodCalls": [[
        |    "Email/query",
        |    {
        |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |      "filter" : {
        |        "hasKeyword": "custom"
        |      },
        |      "sort": [{
        |        "property":"receivedAt",
        |        "isAscending": false
        |      }]
        |    },
        |    "c1"]]
        |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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
        .inPath("$.methodResponses[0][1].ids")
        .isEqualTo(s"""["${messageId4.serialize}", "${messageId3.serialize}", "${messageId2.serialize}", "${messageId1.serialize}"]""")
    }
  }

  @Test
  def hasKeywordSortedByReceivedAtShouldYieldExpectedResultWithOffsetAndLimit(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate2), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate1), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate2), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "limit": 2,
         |      "position": 1
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3, messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 1,
           |                "ids": ["${messageId3.serialize}", "${messageId2.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def hasKeywordAfterSortedByReceivedAtShouldYieldExpectedResult(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate2), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate1), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate2), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom",
         |        "after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId4, messageId3)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId4.serialize}", "${messageId3.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def hasKeywordAfterSortedByReceivedAtShouldYieldExpectedResultWithOffsetAndLimit(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(1)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate2), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate1), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate2), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom",
         |        "after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "limit": 1,
         |      "position": 1
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3)}",
           |                "canCalculateChanges": false,
           |                "position": 1,
           |                "ids": ["${messageId3.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def hasKeywordBeforeSortedByReceivedAtShouldYieldExpectedResult(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val beforeRequestDate3 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val requestDate = ZonedDateTime.now()
    val afterRequestDate1 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate2), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate3), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate1), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom",
         |        "before": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }]
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3, messageId2, messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId3.serialize}", "${messageId2.serialize}", "${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def hasKeywordBeforeSortedByReceivedAtShouldYieldExpectedResultWithOffsetAndLimit(server: GuiceJamesServer): Unit = {
    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val beforeRequestDate3 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val requestDate = ZonedDateTime.now()
    val afterRequestDate1 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate2), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(beforeRequestDate3), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, buildTestMessage, Optional.of(afterRequestDate1), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom",
         |        "before": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "limit": 1,
         |      "position": 1
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2)}",
           |                "canCalculateChanges": false,
           |                "position": 1,
           |                "ids": ["${messageId2.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def collapseThreadsOnKeywordShouldApply(server: GuiceJamesServer): Unit = {
    val thread1Message: Message = buildTestThreadMessage("thread-1", "Message-ID-1")
    val thread2Message: Message = buildTestThreadMessage("thread-2", "Message-ID-2")
    val thread3Message: Message = buildTestThreadMessage("thread-3", "Message-ID-3")

    val fourDaysBefore = Date.from(ZonedDateTime.now().minusDays(4).toInstant)
    val threeDaysBefore = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val twoDaysBefore = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val oneDayBefore = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    // Thread 1: message 1 (oldest), message 2 (newest)
    // Thread 2: message 3
    // Thread 3: message 4
    val messageId1: MessageId = sendMessageToBobInbox(server, thread1Message, Optional.of(twoDaysBefore), new Flags("custom"))
    val messageId2: MessageId = sendMessageToBobInbox(server, thread1Message, Optional.of(oneDayBefore), new Flags("custom"))
    val messageId3: MessageId = sendMessageToBobInbox(server, thread2Message, Optional.of(threeDaysBefore), new Flags("custom"))
    val messageId4: MessageId = sendMessageToBobInbox(server, thread3Message, Optional.of(fourDaysBefore), new Flags("custom"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom"
         |      },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "collapseThreads": true
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: String = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId2, messageId3, messageId4)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId2.serialize}", "${messageId3.serialize}", "${messageId4.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def collapseThreadsOnKeywordShouldApplyPaginationOnCollapsedResults(server: GuiceJamesServer): Unit = {
    val thread1Message: Message = buildTestThreadMessage("thread-1", "Message-ID-1")
    val thread2Message: Message = buildTestThreadMessage("thread-2", "Message-ID-2")
    val thread3Message: Message = buildTestThreadMessage("thread-3", "Message-ID-3")

    val fourDaysBefore = Date.from(ZonedDateTime.now().minusDays(4).toInstant)
    val threeDaysBefore = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val twoDaysBefore = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val oneDayBefore = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val mailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    // Thread 1: message 1 (oldest), message 2 (newest)
    // Thread 2: message 3
    // Thread 3: message 4
    val messageId1: MessageId = sendMessageToBobInbox(server, thread1Message, Optional.of(twoDaysBefore), new Flags("custom"))
    val messageId2: MessageId = sendMessageToBobInbox(server, thread1Message, Optional.of(oneDayBefore), new Flags("custom"))
    val messageId3: MessageId = sendMessageToBobInbox(server, thread2Message, Optional.of(threeDaysBefore), new Flags("custom"))
    val messageId4: MessageId = sendMessageToBobInbox(server, thread3Message, Optional.of(fourDaysBefore), new Flags("custom"))

    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom"
         |      },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "position": 1,
         |      "limit": 2,
         |      "collapseThreads": true
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response: String = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3, messageId4)}",
           |                "canCalculateChanges": false,
           |                "position": 1,
           |                "ids": ["${messageId3.serialize}", "${messageId4.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def hasKeywordAfterSortedByReceivedAtShouldCollapseThreads(server: GuiceJamesServer): Unit = {
    val thread1Message: Message = buildTestThreadMessage("thread-1", "Message-ID-1")
    val thread2Message: Message = buildTestThreadMessage("thread-2", "Message-ID-2")
    val thread3Message: Message = buildTestThreadMessage("thread-3", "Message-ID-3")

    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val requestDate = ZonedDateTime.now().minusDays(2)
    val afterRequestDate1 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val afterRequestDate2 = Date.from(ZonedDateTime.now().toInstant)
    val afterRequestDate3 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1 = sendMessageToBobInbox(server, thread1Message, Optional.of(beforeRequestDate1), new Flags("custom"))

    val messageId2 = sendMessageToBobInbox(server, thread2Message, Optional.of(afterRequestDate1), new Flags("custom"))

    val messageId3 = sendMessageToBobInbox(server, thread2Message, Optional.of(afterRequestDate2), new Flags("custom"))

    val messageId4 = sendMessageToBobInbox(server, thread3Message, Optional.of(afterRequestDate3), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom",
         |        "after": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |       },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "collapseThreads": true
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId4, messageId3)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId4.serialize}", "${messageId3.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  @Test
  def hasKeywordBeforeSortedByReceivedAtShouldCollapseThreads(server: GuiceJamesServer): Unit = {
    val thread1Message: Message = buildTestThreadMessage("thread-1", "Message-ID-1")
    val thread2Message: Message = buildTestThreadMessage("thread-2", "Message-ID-2")
    val thread3Message: Message = buildTestThreadMessage("thread-3", "Message-ID-3")

    val beforeRequestDate1 = Date.from(ZonedDateTime.now().minusDays(3).toInstant)
    val beforeRequestDate2 = Date.from(ZonedDateTime.now().minusDays(2).toInstant)
    val beforeRequestDate3 = Date.from(ZonedDateTime.now().minusDays(1).toInstant)
    val requestDate = ZonedDateTime.now()
    val afterRequestDate1 = Date.from(ZonedDateTime.now().plusDays(1).toInstant)
    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxId = mailboxProbe.createMailbox(MailboxPath.inbox(BOB))

    val messageId1: MessageId = sendMessageToBobInbox(server, thread1Message, Optional.of(beforeRequestDate1), new Flags("custom"))
    val messageId2: MessageId = sendMessageToBobInbox(server, thread2Message, Optional.of(beforeRequestDate2), new Flags("custom"))
    val messageId3: MessageId = sendMessageToBobInbox(server, thread2Message, Optional.of(beforeRequestDate3), new Flags("custom"))
    val messageId4: MessageId = sendMessageToBobInbox(server, thread3Message, Optional.of(afterRequestDate1), new Flags("custom"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [[
         |    "Email/query",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "hasKeyword": "custom",
         |        "before": "${UTCDate(requestDate).asUTC.format(UTC_DATE_FORMAT)}"
         |      },
         |      "sort": [{
         |        "property":"receivedAt",
         |        "isAscending": false
         |      }],
         |      "collapseThreads": true
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response = `given`
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

      assertThatJson(response).isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [[
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "queryState": "${generateQueryState(messageId3, messageId1)}",
           |                "canCalculateChanges": false,
           |                "position": 0,
           |                "limit": 256,
           |                "ids": ["${messageId3.serialize}", "${messageId1.serialize}"]
           |            },
           |            "c1"
           |        ]]
           |}""".stripMargin)
    }
  }

  private def buildTestMessage = {
    Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
  }

  private def sendMessageToBobInbox(server: GuiceJamesServer, message: Message, requestDate: Optional[Date], flags: Flags): MessageId =
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB),
        AppendCommand.builder()
          .withInternalDate(requestDate)
          .withFlags(flags)
          .build(message))
      .getMessageId

  private def buildTestThreadMessage(subject: String, mimeMessageId: String) =
    Message.Builder
      .of
      .setMessageId(mimeMessageId)
      .setSubject(subject)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build


  private def generateQueryState(messages: MessageId*): String =
    Hashing.murmur3_32_fixed()
      .hashUnencodedChars(messages.toList.map(_.serialize).mkString(" "))
      .toString
}
