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

package com.linagora.tmail.integration

import java.util.stream.IntStream

import com.linagora.tmail.integration.RateLimitingPlanIntegrationContract.{DOMAIN, RECIPIENT1, RECIPIENT2, RECIPIENT3, SENDER1, SENDER2}
import io.restassured.RestAssured
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.{SC_CREATED, SC_NO_CONTENT}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailets.configuration.Constants.{LOCALHOST_IP, PASSWORD, awaitAtMostOneMinute}
import org.apache.james.mailrepository.api.MailRepositoryUrl
import org.apache.james.modules.protocols.{ImapGuiceProbe, SmtpGuiceProbe}
import org.apache.james.utils.TestIMAPClient.INBOX
import org.apache.james.utils.{DataProbeImpl, MailRepositoryProbeImpl, SMTPMessageSender, TestIMAPClient, WebAdminGuiceProbe}
import org.apache.james.webadmin.WebAdminUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

object RateLimitingPlanIntegrationContract {
  val DOMAIN: Domain = Domain.of("domain.tld")
  val SENDER1: Username = Username.fromLocalPartWithDomain("sender1", DOMAIN)
  val SENDER2: Username = Username.fromLocalPartWithDomain("sender2", DOMAIN)
  val RECIPIENT1: Username = Username.fromLocalPartWithDomain("recipient1", DOMAIN)
  val RECIPIENT2: Username = Username.fromLocalPartWithDomain("recipient2", DOMAIN)
  val RECIPIENT3: Username = Username.fromLocalPartWithDomain("recipient3", DOMAIN)
}

trait RateLimitingPlanIntegrationContract {

  def getErrorRepository: MailRepositoryUrl

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(SENDER1.asString(), PASSWORD)
      .addUser(SENDER2.asString(), PASSWORD)
      .addUser(RECIPIENT1.asString(), PASSWORD)
      .addUser(RECIPIENT2.asString(), PASSWORD)
      .addUser(RECIPIENT3.asString(), PASSWORD)

    requestSpecification = WebAdminUtils.buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .build
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails()
  }

  private def messageSender(server: GuiceJamesServer): SMTPMessageSender =
    new SMTPMessageSender(DOMAIN.asString())
      .connect(LOCALHOST_IP, server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)

  private def testImapClient(server: GuiceJamesServer): TestIMAPClient =
    new TestIMAPClient().connect(LOCALHOST_IP, server.getProbe(classOf[ImapGuiceProbe]).getImapPort)

  private def createNewPlan(): String = {
    val json: String =
      """{
        |  "transitLimits": [
        |    {
        |      "name": "receivedMailsPerHour",
        |      "periodInSeconds": 3600,
        |      "count": 1,
        |      "size": 2048
        |    }
        |  ],
        |  "relayLimits": [
        |    {
        |      "name": "relayMailsPerHour",
        |      "periodInSeconds": 3600,
        |      "count": 1,
        |      "size": 2048
        |    }
        |  ],
        |  "deliveryLimits": [
        |    {
        |      "name": "deliveryMailsPerHour",
        |      "periodInSeconds": 3600,
        |      "count": 1,
        |      "size": 2048
        |    }
        |  ]
        |}""".stripMargin

    createNewPlan(json, "planTest")
  }

  private def createNewPlan(limitJson: String, planName: String): String =
    given().basePath("/rate-limit-plans/" + planName)
      .body(limitJson)
    .when()
      .post()
    .`then`()
      .statusCode(SC_CREATED)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("planId").asInstanceOf[String]


  private def attachPlanToUser(planId: String, username: Username): Unit =
    given().basePath(String.format("/users/%s/rate-limit-plans/%s", username.asString(), planId))
      .put()
    .`then`()
      .statusCode(SC_NO_CONTENT)

  @Test
  def senderShouldSentEmailWhenRateLimitPlanIsAcceptable(server: GuiceJamesServer): Unit = {
    attachPlanToUser(createNewPlan(), SENDER1)

    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT1.asString, "subject: test123\r\rcontent 123\r.\r")

    val imapClient: TestIMAPClient = testImapClient(server)
    imapClient.login(RECIPIENT1.asString(), PASSWORD)
      .select(INBOX)
      .awaitMessage(awaitAtMostOneMinute)

    assertThat(imapClient.readFirstMessage).contains("content 123")
  }

  @Test
  def senderShouldNotSentEmailWhenRateLimitPlanIsExceeded(server: GuiceJamesServer): Unit = {
    attachPlanToUser(createNewPlan(), SENDER1)

    // accept
    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT1.asString, "subject: test1\r\rcontent 1\r.\r")

    testImapClient(server).login(RECIPIENT1.asString(), PASSWORD)
      .select(INBOX)
      .awaitMessage(awaitAtMostOneMinute)

    // exceed
    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT2.asString, "subject: test2\r\rcontent 2\r.\r")

    awaitAtMostOneMinute.until(() => server.getProbe(classOf[MailRepositoryProbeImpl])
      .getRepositoryMailCount(getErrorRepository) == 1)

    assertThat(testImapClient(server)
      .login(RECIPIENT2, PASSWORD)
      .getMessageCount(INBOX))
      .isEqualTo(0)
  }

  @Test
  def recipientShouldReceivedEmailWhenRateLimitPlanIsAcceptable(server: GuiceJamesServer): Unit = {
    attachPlanToUser(createNewPlan(), RECIPIENT1)

    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT1.asString, "subject: test123\r\rcontent 123\r.\r")

    val imapClient: TestIMAPClient = testImapClient(server)
    imapClient.login(RECIPIENT1.asString(), PASSWORD)
      .select(INBOX)
      .awaitMessage(awaitAtMostOneMinute)

    assertThat(imapClient.readFirstMessage).contains("content 123")
  }

  @Test
  def recipientShouldNotReceivedEmailWhenRateLimitPlanIsExceeded(server: GuiceJamesServer): Unit = {
    attachPlanToUser(createNewPlan(), RECIPIENT1)
    // accept
    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT1.asString, "subject: test1\r\rcontent 1\r.\r")

    testImapClient(server).login(RECIPIENT1.asString(), PASSWORD)
      .select(INBOX)
      .awaitMessage(awaitAtMostOneMinute)

    // exceed
    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT1.asString, "subject: test2\r\rcontent 2\r.\r")

    awaitAtMostOneMinute.until(() => server.getProbe(classOf[MailRepositoryProbeImpl])
      .getRepositoryMailCount(getErrorRepository) == 1)

    assertThat(testImapClient(server)
      .login(RECIPIENT1, PASSWORD)
      .getMessageCount(INBOX))
      .isEqualTo(1)
  }

  @Test
  def differentPlansApplyToDifferentUsersShouldRateLimitPerSender(server: GuiceJamesServer): Unit = {
    val planA: String = createNewPlan(
      """{
        |  "transitLimits": [
        |    {
        |      "name": "receivedMailsPerHour",
        |      "periodInSeconds": 3600,
        |      "count": 1,
        |      "size": 2048
        |    }
        |  ]
        |}""".stripMargin, "planA")

    val countLimitPlanB: Int = 5
    val planB: String = createNewPlan(
      s"""{
         |  "transitLimits": [
         |    {
         |      "name": "receivedMailsPerHour",
         |      "periodInSeconds": 3600,
         |      "count": $countLimitPlanB,
         |      "size": 2000048
         |    }
         |  ]
         |}""".stripMargin, "planB")

    attachPlanToUser(planA, SENDER1)
    attachPlanToUser(planB, SENDER2)

    // Checking sender1 with planA
    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT1.asString, "subject: test1\r\rcontent 1\r.\r")

    testImapClient(server).login(RECIPIENT1.asString(), PASSWORD)
      .select(INBOX)
      .awaitMessage(awaitAtMostOneMinute)

    messageSender(server).authenticate(SENDER1.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER1.asString, RECIPIENT2.asString, "subject: test2\r\rcontent 2\r.\r")

    awaitAtMostOneMinute.until(() => server.getProbe(classOf[MailRepositoryProbeImpl])
      .getRepositoryMailCount(getErrorRepository) == 1)

    assertThat(testImapClient(server)
      .login(RECIPIENT2, PASSWORD)
      .getMessageCount(INBOX))
      .isEqualTo(0)

    // Checking sender2 with planB
    IntStream.range(0, countLimitPlanB)
      .forEach(index =>
        messageSender(server).authenticate(SENDER2.asString(), PASSWORD)
          .sendMessageWithHeaders(SENDER2.asString, RECIPIENT3.asString, s"""subject: test3+$index\r\rcontent 3\r.\r"""))

    awaitAtMostOneMinute.until(() => testImapClient(server)
      .login(RECIPIENT3, PASSWORD)
      .getMessageCount(INBOX) == countLimitPlanB)

    messageSender(server).authenticate(SENDER2.asString(), PASSWORD)
      .sendMessageWithHeaders(SENDER2.asString, RECIPIENT3.asString, s"""subject: test4\r\rcontent 4\r.\r""")
    awaitAtMostOneMinute.until(() => server.getProbe(classOf[MailRepositoryProbeImpl])
      .getRepositoryMailCount(getErrorRepository) == 2)
  }

}
