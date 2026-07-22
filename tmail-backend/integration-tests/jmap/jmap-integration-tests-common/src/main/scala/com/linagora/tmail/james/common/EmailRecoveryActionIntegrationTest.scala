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
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import com.linagora.tmail.james.common.LinagoraEmailSendMethodContract.HTML_BODY
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.{await => awaitility}
import org.awaitility.core.ConditionFactory
import org.hamcrest.Matchers
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.{BeforeEach, Test}

object EmailRecoveryActionIntegrationTest {
  case class TestContext(bobUsername: Username,
                         bobAccountId: String,
                         bobBaseRequest: RequestSpecification,
                         bobInboxId: String,
                         andreUsername: Username,
                         andreAccountId: String,
                         andreBaseRequest: RequestSpecification,
                         andreInboxId: String)

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait EmailRecoveryActionIntegrationTest {
  import EmailRecoveryActionIntegrationTest.TestContext

  private lazy val await: ConditionFactory = awaitility.atMost(30, TimeUnit.SECONDS)

  def bobUsername: Username = EmailRecoveryActionIntegrationTest.currentContext.get().bobUsername
  def bobAccountId: String = EmailRecoveryActionIntegrationTest.currentContext.get().bobAccountId
  def bobBaseRequest: RequestSpecification = EmailRecoveryActionIntegrationTest.currentContext.get().bobBaseRequest
  def bobInboxId: String = EmailRecoveryActionIntegrationTest.currentContext.get().bobInboxId
  def andreUsername: Username = EmailRecoveryActionIntegrationTest.currentContext.get().andreUsername
  def andreAccountId: String = EmailRecoveryActionIntegrationTest.currentContext.get().andreAccountId
  def andreBaseRequest: RequestSpecification = EmailRecoveryActionIntegrationTest.currentContext.get().andreBaseRequest
  def andreInboxId: String = EmailRecoveryActionIntegrationTest.currentContext.get().andreInboxId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(bob.asString(), BOB_PASSWORD)
      .addUser(andre.asString(), ANDRE_PASSWORD)

    val andreInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(andre))
      .serialize()

    val bobInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(bob))
      .serialize()

    val bobBaseRequest = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    val andreBaseRequest = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(andre, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    EmailRecoveryActionIntegrationTest.currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString,
      bobBaseRequest = bobBaseRequest,
      bobInboxId = bobInboxId,
      andreUsername = andre,
      andreAccountId = Hashing.sha256().hashString(andre.asString(), StandardCharsets.UTF_8).toString,
      andreBaseRequest = andreBaseRequest,
      andreInboxId = andreInboxId))
  }

  @Test
  def recoveryShouldRestoreJmapDeletedEmail(): Unit = {
    // bob send an email to andre
    bobSendAnEmailToAndre()

    val andreMessageId: String = awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 1).head // andre receive the email

    // andre delete the email
    `given`(andreBaseRequest)
      .body(
        s"""{ "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [
           |      ["Email/set",{
           |        "accountId": "$andreAccountId",
           |        "destroy": ["$andreMessageId"]
           |      }, "c1"],
           |      [ "Email/get", {
           |        "accountId": "$andreAccountId",
           |        "ids": ["$andreMessageId"]
           |      }, "c2" ]] }""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .body("methodResponses[0][1].destroyed", Matchers.containsInAnyOrder(andreMessageId))
      .body("methodResponses[1][1].list", Matchers.empty())
      .body("methodResponses[1][1].notFound", Matchers.containsInAnyOrder(andreMessageId))

    // andre restore the email by JMAP EmailRecoveryAction/set
    andrePostEmailRecoveryCreateAction()

    // verify the restore email success (the email is in andre's inbox)
    await.untilAsserted(() => {
      `given`(andreBaseRequest)
        .body(jmapEmailQueryAllOfAndre)
      .when()
        .post()
      .`then`
        .body("methodResponses[1][1].list[0].subject", Matchers.is("World domination"))
    })
  }

  @Test
  def recoveryShouldNotImpactOtherUsers(): Unit = {
    bobSendAnEmailToAndre()
    awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 1)
    deleteAllEmail(andreBaseRequest, andreAccountId)
    deleteAllEmail(bobBaseRequest, bobAccountId)
    andrePostEmailRecoveryCreateAction()
    awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 1)

    // bob should not impact by andre's recovery action
    await.untilAsserted(() => {
      `given`(bobBaseRequest)
        .body(
          s"""{ "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
             |  "methodCalls": [[
             |      "Email/query",
             |      {
             |        "accountId": "$bobAccountId",
             |        "filter": {}
             |      }, "c1"]] }""".stripMargin)
      .when()
        .post()
      .`then`
        .body("methodResponses[0][1].ids", Matchers.empty())
    })
  }

  @Test
  def recoveryShouldNotBeRemovedFromTheVault(): Unit = {
    bobSendAnEmailToAndre()
    awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 1)
    deleteAllEmail(andreBaseRequest, andreAccountId)
    andrePostEmailRecoveryCreateAction()
    awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 1)

    // duplicate recovery
    andrePostEmailRecoveryCreateAction()

    // then
    awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 2)
  }

  @Test
  def recoveryShouldNotRestoreUnMatchingEmail(): Unit = {
    bobSendAnEmailToAndre(emailSubjectSuffix = Some(" Newyork"))
    bobSendAnEmailToAndre(emailSubjectSuffix = Some(" Tokyo"))
    awaitAndreAlreadyHasExpectedEmail(expectEmailNumber = 2)
    deleteAllEmail(andreBaseRequest, andreAccountId)

    andrePostEmailRecoveryCreateAction(subjectQuery = "Tokyo");

    await.untilAsserted(() => {
      `given`(andreBaseRequest)
        .body(jmapEmailQueryAllOfAndre)
      .when()
        .post()
      .`then`
        .body("methodResponses[1][1].list", hasSize(1))
        .body("methodResponses[1][1].list[0].subject", Matchers.is("World domination Tokyo"))
    })
  }

  private def bobSendAnEmailToAndre(emailSubjectSuffix: Option[String] = None): Unit = {
    `given`(bobBaseRequest)
      .body(
        s"""{ "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail",
           |            "urn:ietf:params:jmap:submission", "com:linagora:params:jmap:pgp"],
           |  "methodCalls": [[
           |      "Email/send",
           |      {
           |        "accountId": "$bobAccountId",
           |        "create": {
           |          "K87": {
           |            "email/create": {
           |              "mailboxIds": {"${bobInboxId}": true},
           |              "subject": "World domination${emailSubjectSuffix.getOrElse("")}",
           |              "htmlBody": [{"partId": "a49d", "type": "text/html"}],
           |              "bodyValues": {
           |                "a49d": {
           |                  "value": "$HTML_BODY",
           |                  "isTruncated": false,
           |                  "isEncodingProblem": false
           |                }
           |              }
           |            },
           |            "emailSubmission/set": {
           |              "envelope": {
           |                "mailFrom": {"email": "${bobUsername.asString}"},
           |                "rcptTo": [{"email": "${andreUsername.asString}"}]
           |              }
           |            }
           |          }
           |        }
           |      }, "c1"]] }""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .body("methodResponses[0][1].created.K87.emailSubmissionId", Matchers.notNullValue())
  }

  // return the email ids of andre's inbox
  private def awaitAndreAlreadyHasExpectedEmail(expectEmailNumber: Int = 1): Seq[String] = {
    import scala.jdk.CollectionConverters._
    var response: Seq[String] = Seq()
    await.untilAsserted(() => {
      val emailQueryResponse: java.util.List[String] = `given`(andreBaseRequest)
        .body(jmapEmailQueryAllOfAndre)
      .when()
        .post()
      .`then`
        .statusCode(HttpStatus.SC_OK)
        .contentType(JSON)
        .extract()
        .jsonPath()
        .getList("methodResponses[0][1].ids")
      assertThat(emailQueryResponse).hasSize(expectEmailNumber)
      response = emailQueryResponse.asScala.toSeq
    })
    response
  }

  private def deleteAllEmail(requestSpecification: RequestSpecification = andreBaseRequest,
                             accountId: String = andreAccountId): Unit = {
    `given`(requestSpecification)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail"
           |  ],
           |  "methodCalls": [
           |    [ "Email/query", {
           |      "accountId": "$accountId",
           |      "filter": {}
           |    }, "c1" ],
           |    [ "Email/set", {
           |        "accountId": "$accountId",
           |        "#destroy": {
           |          "resultOf": "c1",
           |          "name": "Email/query",
           |          "path": "ids/*"
           |        }
           |      }, "c2" ],
           |    [ "Email/get", {
           |        "accountId": "$accountId",
           |        "properties": [ "id", "subject" ],
           |        "#ids": {
           |          "resultOf": "c1",
           |          "name": "Email/query",
           |          "path": "ids/*"
           |        }
           |      }, "c3" ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .body("methodResponses[2][1].list", Matchers.empty())
  }

  private def andrePostEmailRecoveryCreateAction(subjectQuery: String = "World domination"): Unit = {
    val emailRecoveryActionSetResponse: String = `given`(andreBaseRequest)
      .body(
        s"""{ "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |  "methodCalls": [
           |      ["EmailRecoveryAction/set",{
           |        "accountId": "$andreAccountId",
           |        "create": {
           |          "K87": {
           |            "subject": "$subjectQuery"
           |          }
           |        }
           |      }, "c1"],
           |      [ "EmailRecoveryAction/get", {
           |        "ids": ["#K87"],
           |        "properties": []
           |      }, "c2" ]] }""".stripMargin)
    .when()
      .post()
    .`then`()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(emailRecoveryActionSetResponse)
      .inPath("methodResponses")
      .isEqualTo(
        s"""[
           |  [ "EmailRecoveryAction/set", {
           |      "created": {
           |        "K87": { "id": "$${json-unit.ignore}" }
           |      }
           |    }, "c1"],
           |  [ "EmailRecoveryAction/get", {
           |      "notFound": [],
           |      "list": [
           |        { "id": "$${json-unit.ignore}" }
           |      ] }, "c2"
           |  ]
           |]""".stripMargin)
  }

  private def jmapEmailQueryAllOfAndre: String =
    s"""{ "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"
      |  ],
      |  "methodCalls": [
      |    [ "Email/query", {
      |        "accountId": "$andreAccountId",
      |        "filter": {}
      |      }, "c1" ],
      |    [ "Email/get", {
      |        "accountId": "$andreAccountId",
      |        "properties": [ "id", "subject" ],
      |        "#ids": {
      |          "resultOf": "c1",
      |          "name": "Email/query",
      |          "path": "ids/*"
      |        }
      |      }, "c2" ]
      |  ] }""".stripMargin
}