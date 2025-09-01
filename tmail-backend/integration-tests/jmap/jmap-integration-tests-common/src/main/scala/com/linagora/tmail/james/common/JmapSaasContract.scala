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

import com.linagora.tmail.common.probe.SaaSProbe
import com.linagora.tmail.saas.model.{SaaSAccount}
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers.{equalTo, hasKey, not}
import org.junit.jupiter.api.{AfterEach, Tag, Test}

trait JmapSaasContract {

  def startJmapServer(saasSupport: Boolean): GuiceJamesServer

  def stopJmapServer(): Unit

  def publishAmqpSettingsMessage(message: String): Unit

  private def setUpJmapServer(saasSupport: Boolean = false): GuiceJamesServer = {
    val server = startJmapServer(saasSupport)
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
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
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldReturnAttachedPlan(): Unit = {
    val server: GuiceJamesServer = setUpJmapServer(saasSupport = true)

    server.getProbe(classOf[SaaSProbe])
      .setPlan(BOB, new SaaSAccount(true, true))

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
         |    "username": "${BOB.asString()}",
         |    "isPaying": true,
         |    "canUpgrade": true,
         |    "mail": {
         |        "storageQuota": 1234
         |    }
         |}""".stripMargin)

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
         |    "username": "${BOB.asString()}",
         |    "isPaying": false,
         |    "canUpgrade": true,
         |    "mail": {
         |        "storageQuota": 1234
         |    }
         |}""".stripMargin)

    publishAmqpSettingsMessage(
      s"""{
         |    "username": "${BOB.asString()}",
         |    "isPaying": true,
         |    "canUpgrade": true,
         |    "mail": {
         |        "storageQuota": 10000
         |    }
         |}""".stripMargin)

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
}
