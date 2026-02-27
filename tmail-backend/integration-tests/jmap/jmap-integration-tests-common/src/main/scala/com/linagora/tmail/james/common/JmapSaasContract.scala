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

import java.util.Optional

import com.linagora.tmail.common.probe.SaaSProbe
import com.linagora.tmail.james.common.JmapSaasContract.{DOMAIN_SUBSCRIPTION_ROUTING_KEY, QUOTA_ROOT, SUBSCRIPTION_ROUTING_KEY, TEST_ACCOUNT_ID, TEST_DOMAIN, TEST_PASSWORD, TEST_USER}
import com.linagora.tmail.james.common.probe.DomainProbe
import com.linagora.tmail.saas.model.SaaSAccount
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.quota.QuotaSizeLimit
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.{MailboxPath, QuotaRoot}
import org.apache.james.modules.{MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{equalTo, hasKey, not}
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.{AfterEach, Tag, Test}

object JmapSaasContract {
  val SUBSCRIPTION_ROUTING_KEY: String = "saas.subscription.routingKey"
  val DOMAIN_SUBSCRIPTION_ROUTING_KEY: String = "domain.subscription.changed"
  val TEST_DOMAIN: Domain = Domain.of("james.org")
  val TEST_USER: Username = Username.fromLocalPartWithDomain("james-user", TEST_DOMAIN)
  val TEST_PASSWORD: String = "secret"
  val TEST_ACCOUNT_ID: String = AccountId.from(TEST_USER).fold(throw _, _.id.value)
  val QUOTA_ROOT: QuotaRoot = QuotaRoot.quotaRoot("#private&" + TEST_USER.asString(), Optional.of(TEST_DOMAIN))
}

trait JmapSaasContract {

  def startJmapServer(saasSupport: Boolean): GuiceJamesServer

  def stopJmapServer(): Unit

  def publishAmqpSettingsMessage(message: String, routingKey: String): Unit

  def saaSSignatureProvisionConfigured: Boolean = false

  private def setUpJmapServer(saasSupport: Boolean = false): GuiceJamesServer = {
    val server = startJmapServer(saasSupport)
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(TEST_DOMAIN.asString())
      .addUser(TEST_USER.asString(), TEST_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(TEST_USER, TEST_PASSWORD)))
      .build

    server
  }

  @AfterEach
  def tearDown(): Unit = stopJmapServer()

  @Test
  def shouldNotReturnSaaSCapabilityByDefaultWhenSaaSDisabled(): Unit = {
    setUpJmapServer()

    `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", not(hasKey("com:linagora:params:saas")))
  }

  @Test
  def shouldReturnSaaSCapabilityWhenSaaSModuleEnabled(): Unit = {
    setUpJmapServer(saasSupport = true)

    `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:saas"))
  }

  @Test
  def shouldReturnFreePlanByDefault(): Unit = {
    setUpJmapServer(saasSupport = true)

    `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.'com:linagora:params:saas'.isPaying", equalTo(false))
      .body("capabilities.'com:linagora:params:saas'.canUpgrade", equalTo(true))
  }

  @Test
  def shouldProvisionSaaSSignatureForNonPayingUser(): Unit = {
    assumeTrue(saaSSignatureProvisionConfigured)
    val server = setUpJmapServer(saasSupport = true)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(TEST_USER))

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$TEST_ACCOUNT_ID",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      `given`()
        .body(request)
        .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .body(s"methodResponses[0][1].list.findAll { it.email == '${TEST_USER.asString()}' && it.mayDelete == true }.size()", equalTo(1))
        .body(s"methodResponses[0][1].list.find { it.email == '${TEST_USER.asString()}' && it.mayDelete == true }.textSignature", equalTo("Register on https://sign-up.twake.app !"))
        .body(s"methodResponses[0][1].list.find { it.email == '${TEST_USER.asString()}' && it.mayDelete == true }.htmlSignature", equalTo("<p>Register on <a href=\"https://sign-up.twake.app\">Twake</a> !</p>"))
    }
  }

  @Test
  def shouldNotProvisionSaaSSignatureForPayingUser(): Unit = {
    assumeTrue(saaSSignatureProvisionConfigured)

    val server = setUpJmapServer(saasSupport = true)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(TEST_USER))
    server.getProbe(classOf[SaaSProbe])
      .setPlan(TEST_USER, new SaaSAccount(true, true))

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$TEST_ACCOUNT_ID",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      `given`()
        .body(request)
        .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .body(s"methodResponses[0][1].list.findAll { it.email == '${TEST_USER.asString()}' && it.mayDelete == true }.size()", equalTo(1))
        .body(s"methodResponses[0][1].list.find { it.email == '${TEST_USER.asString()}' && it.mayDelete == true }.textSignature", equalTo(""))
        .body(s"methodResponses[0][1].list.find { it.email == '${TEST_USER.asString()}' && it.mayDelete == true }.htmlSignature", equalTo(""))
    }
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldReturnAttachedPlan(): Unit = {
    val server: GuiceJamesServer = setUpJmapServer(saasSupport = true)

    server.getProbe(classOf[SaaSProbe])
      .setPlan(TEST_USER, new SaaSAccount(true, true))

    `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.'com:linagora:params:saas'.isPaying", equalTo(true))
      .body("capabilities.'com:linagora:params:saas'.canUpgrade", equalTo(true))
  }

  @Test
  def planNameShouldBeSetWhenSubscriptionUpdateAndUserHasNoPlanYet(): Unit = {
    setUpJmapServer(saasSupport = true)

    publishAmqpSettingsMessage(
      s"""{
         |    "internalEmail": "${TEST_USER.asString()}",
         |    "isPaying": true,
         |    "canUpgrade": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 1234,
         |        "mailsSentPerMinute": 10,
         |        "mailsSentPerHour": 100,
         |        "mailsSentPerDay": 1000,
         |        "mailsReceivedPerMinute": 20,
         |        "mailsReceivedPerHour": 200,
         |        "mailsReceivedPerDay": 2000
         |      }
         |    }
         |}""".stripMargin,
      SUBSCRIPTION_ROUTING_KEY)

    awaitAtMostTenSeconds.untilAsserted { () =>
      `given`()
        .when()
        .get("/session")
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .body("capabilities.'com:linagora:params:saas'.isPaying", equalTo(true))
        .body("capabilities.'com:linagora:params:saas'.canUpgrade", equalTo(true))
    }
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def planNameShouldBeUpdatedWhenSubscriptionUpdateAndUserAlreadyHasAPlan(): Unit = {
    setUpJmapServer(saasSupport = true)

    publishAmqpSettingsMessage(
      s"""{
         |    "internalEmail": "${TEST_USER.asString()}",
         |    "isPaying": false,
         |    "canUpgrade": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 1234,
         |        "mailsSentPerMinute": 1,
         |        "mailsSentPerHour": 1,
         |        "mailsSentPerDay": 1,
         |        "mailsReceivedPerMinute": 1,
         |        "mailsReceivedPerHour": 1,
         |        "mailsReceivedPerDay": 1
         |      }
         |    }
         |}""".stripMargin,
      SUBSCRIPTION_ROUTING_KEY)

    publishAmqpSettingsMessage(
      s"""{
         |    "internalEmail": "${TEST_USER.asString()}",
         |    "isPaying": true,
         |    "canUpgrade": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 10000,
         |        "mailsSentPerMinute": 10,
         |        "mailsSentPerHour": 100,
         |        "mailsSentPerDay": 1000,
         |        "mailsReceivedPerMinute": 20,
         |        "mailsReceivedPerHour": 200,
         |        "mailsReceivedPerDay": 2000
         |      }
         |    }
         |}""".stripMargin,
      SUBSCRIPTION_ROUTING_KEY)

    awaitAtMostTenSeconds.untilAsserted { () =>
      `given`()
        .when()
        .get("/session")
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .body("capabilities.'com:linagora:params:saas'.isPaying", equalTo(true))
        .body("capabilities.'com:linagora:params:saas'.canUpgrade", equalTo(true))
    }
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def domainShouldBeCreatedWhenDomainSubscriptionValidated(): Unit = {
    val server = setUpJmapServer(saasSupport = true)
    val domain: Domain = Domain.of("twake.app")

    publishAmqpSettingsMessage(
      s"""{
         |    "domain": "%s",
         |    "validated": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 10000,
         |        "mailsSentPerMinute": 10,
         |        "mailsSentPerHour": 100,
         |        "mailsSentPerDay": 1000,
         |        "mailsReceivedPerMinute": 20,
         |        "mailsReceivedPerHour": 200,
         |        "mailsReceivedPerDay": 2000
         |      }
         |    }
         |}""".format(domain.asString())
        .stripMargin,
      DOMAIN_SUBSCRIPTION_ROUTING_KEY)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[DomainProbe]).containsDomain(domain)).isTrue)
  }

  @Test
  def quotaForUserShouldBeDomainByDefaultWhenDefined(): Unit = {
    val server = setUpJmapServer(saasSupport = true)

    publishAmqpSettingsMessage(
      s"""{
         |    "domain": "%s",
         |    "validated": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 10000,
         |        "mailsSentPerMinute": 10,
         |        "mailsSentPerHour": 100,
         |        "mailsSentPerDay": 1000,
         |        "mailsReceivedPerMinute": 20,
         |        "mailsReceivedPerHour": 200,
         |        "mailsReceivedPerDay": 2000
         |      }
         |    }
         |}""".format(TEST_DOMAIN.asString())
        .stripMargin,
      DOMAIN_SUBSCRIPTION_ROUTING_KEY)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[QuotaProbesImpl]).getMaxStorage(QUOTA_ROOT))
        .isEqualTo(Optional.of(QuotaSizeLimit.size(10000))))
  }

  @Test
  def quotaForUserShouldBeUserByDefaultWhenDomainAndUserQuotaDefined(): Unit = {
    val server = setUpJmapServer(saasSupport = true)

    publishAmqpSettingsMessage(
      s"""{
         |    "domain": "%s",
         |    "validated": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 10000,
         |        "mailsSentPerMinute": 10,
         |        "mailsSentPerHour": 100,
         |        "mailsSentPerDay": 1000,
         |        "mailsReceivedPerMinute": 20,
         |        "mailsReceivedPerHour": 200,
         |        "mailsReceivedPerDay": 2000
         |      }
         |    }
         |}""".format(TEST_DOMAIN.asString())
        .stripMargin,
      DOMAIN_SUBSCRIPTION_ROUTING_KEY)

    publishAmqpSettingsMessage(
      s"""{
         |    "internalEmail": "${TEST_USER.asString()}",
         |    "isPaying": true,
         |    "canUpgrade": true,
         |    "features": {
         |      "mail": {
         |        "storageQuota": 20000,
         |        "mailsSentPerMinute": 20,
         |        "mailsSentPerHour": 200,
         |        "mailsSentPerDay": 2000,
         |        "mailsReceivedPerMinute": 30,
         |        "mailsReceivedPerHour": 300,
         |        "mailsReceivedPerDay": 3000
         |      }
         |    }
         |}""".stripMargin,
      SUBSCRIPTION_ROUTING_KEY)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[QuotaProbesImpl]).getMaxStorage(QUOTA_ROOT))
      .isEqualTo(Optional.of(QuotaSizeLimit.size(20000))))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def domainShouldBeRemovedWhenDomainSubscriptionDisabled(): Unit = {
    val server = setUpJmapServer(saasSupport = true)

    publishAmqpSettingsMessage(
      s"""{
         |    "domain": "%s",
         |    "enabled": false
         |}""".format(TEST_DOMAIN.asString())
        .stripMargin,
      DOMAIN_SUBSCRIPTION_ROUTING_KEY)

    awaitAtMostTenSeconds.untilAsserted(() => assertThat(server.getProbe(classOf[DomainProbe]).containsDomain(TEST_DOMAIN)).isFalse)
  }
}
