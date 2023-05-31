package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.EmailRecoveryActionIntegrationTest.{andreBaseRequest, andreInboxId, bobBaseRequest, bobInboxId}
import com.linagora.tmail.james.common.LinagoraEmailSendMethodContract.HTML_BODY
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.`given`
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCOUNT_ID => BOB_ACCOUNT_ID, _}
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.{await => awaitility}
import org.awaitility.core.ConditionFactory
import org.hamcrest.Matchers
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.{BeforeEach, Test}

import java.util.concurrent.TimeUnit

object EmailRecoveryActionIntegrationTest {
  var bobBaseRequest: RequestSpecification = _
  var andreBaseRequest: RequestSpecification = _
  var andreInboxId: String = _
  var bobInboxId: String = _
}

trait EmailRecoveryActionIntegrationTest {
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
      .serialize()

    bobInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))
      .serialize()

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
           |        "accountId": "$ANDRE_ACCOUNT_ID",
           |        "destroy": ["$andreMessageId"]
           |      }, "c1"],
           |      [ "Email/get", {
           |        "accountId": "$ANDRE_ACCOUNT_ID",
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
    deleteAllEmail(andreBaseRequest, ANDRE_ACCOUNT_ID)
    deleteAllEmail(bobBaseRequest, BOB_ACCOUNT_ID)
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
             |        "accountId": "$BOB_ACCOUNT_ID",
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
    deleteAllEmail(andreBaseRequest, ANDRE_ACCOUNT_ID)
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
    deleteAllEmail(andreBaseRequest, ANDRE_ACCOUNT_ID)

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
           |        "accountId": "$BOB_ACCOUNT_ID",
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
           |                "mailFrom": {"email": "${BOB.asString}"},
           |                "rcptTo": [{"email": "${ANDRE.asString}"}]
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
                             accountId: String = ANDRE_ACCOUNT_ID): Unit = {
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
           |        "accountId": "$ANDRE_ACCOUNT_ID",
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
      |        "accountId": "$ANDRE_ACCOUNT_ID",
      |        "filter": {}
      |      }, "c1" ],
      |    [ "Email/get", {
      |        "accountId": "$ANDRE_ACCOUNT_ID",
      |        "properties": [ "id", "subject" ],
      |        "#ids": {
      |          "resultOf": "c1",
      |          "name": "Email/query",
      |          "path": "ids/*"
      |        }
      |      }, "c2" ]
      |  ] }""".stripMargin
}