package com.linagora.tmail.james.common

import java.nio.charset.StandardCharsets

import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.config.ParamConfig
import io.restassured.config.ParamConfig.UpdateStrategy.REPLACE
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.{MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.{DataProbeImpl, WebAdminGuiceProbe}
import org.apache.james.webadmin.WebAdminUtils
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.{BeforeEach, Test}

class TeamMailboxesQuotaExtensionsContract {
  private var webAdminApi: RequestSpecification = _
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(CEDRIC.asString(), "CEDRIC_pass")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
    webAdminApi = WebAdminUtils.buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .build()
      .config(WebAdminUtils.defaultConfig.paramConfig(new ParamConfig(REPLACE, REPLACE, REPLACE)))
  }

  @Test
  def teamMailboxesShouldIndexQuotas(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    // 2 messages on the team mailbox
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(teamMailbox.quotaRoot, QuotaCountLimit.count(4L))
    quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(100 * 1024 * 1024))

    calmlyAwait.untilAsserted(() => {
      val response = `given`().spec(webAdminApi)
        .basePath("/quota/users")
      .when()
        .get()
      .`then`()
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body()
        .asString()

      assertThatJson(response)
        .isArray().containsAnyOf(
        s"""{
           |        "detail": {
           |            "global": {
           |                "count": null,
           |                "size": 104857600
           |            },
           |            "domain": null,
           |            "user": {
           |                "count": 4,
           |                "size": null
           |            },
           |            "computed": {
           |                "count": 4,
           |                "size": 104857600
           |            },
           |            "occupation": {
           |                "size": 170,
           |                "count": 2,
           |                "ratio": {
           |                    "size": 1.621246337890625E-6,
           |                    "count": 0.5,
           |                    "max": 0.5
           |                }
           |            }
           |        },
           |        "username": "marketing@domain.tld"
           |}""".stripMargin)
    })
  }

  @Test
  def teamMailboxesShouldSendOverQuotaEmails(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(teamMailbox.quotaRoot, QuotaCountLimit.count(40L))
    quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(100 * 1024 * 1024))

    // 38 messages on the team mailbox
    for(_ <- 1 to 38) {
      server.getProbe(classOf[MailboxProbeImpl])
        .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
        .getMessageId.serialize()
    }


    calmlyAwait.untilAsserted(() => {
      val request =
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [["Email/query", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "filter": {}
           |    }, "c1"], [
           |     "Email/get",
           |     {
           |       "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |       "properties": ["id", "subject"],
           |       "#ids": {
           |         "resultOf":"c1",
           |         "name":"Email/query",
           |         "path":"ids/*"
           |       }
           |     },
           |     "c2"]]
           |}""".stripMargin

      `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when()
        .post()
      .`then`
        .body("methodResponses[1][1].list.subject", hasItem("Warning: Your email usage just exceeded a configured threshold"));
    })
  }
}
