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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers.{equalTo, hasKey}
import org.junit.jupiter.api.{BeforeEach, Test}

object MemoryMessageVaultCapabilityContract {

  trait EmailRecoveryConfigured {

    @BeforeEach
    def setUp(server: GuiceJamesServer): Unit = {
      server.getProbe(classOf[DataProbeImpl])
        .fluent()
        .addDomain(DOMAIN.asString())
        .addUser(BOB.asString(), BOB_PASSWORD)

      requestSpecification = baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
        .build
    }

    @Test
    def shouldReturnCorrectInfoInMessageVaultCapability(): Unit = {
      val maxEmailRecoveryPerRequest = "6"
      val restorationHorizon = "14 days"

      `given`()
      .when()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .get("/session")
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .body("capabilities", hasKey("com:linagora:params:jmap:messages:vault"))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'", hasKey("maxEmailRecoveryPerRequest"))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'.maxEmailRecoveryPerRequest", equalTo(maxEmailRecoveryPerRequest))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'", hasKey("restorationHorizon"))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'.restorationHorizon", equalTo(restorationHorizon))
    }
  }

  trait EmailRecoveryNotConfigured {

    @BeforeEach
    def setUp(server: GuiceJamesServer): Unit = {
      server.getProbe(classOf[DataProbeImpl])
        .fluent()
        .addDomain(DOMAIN.asString())
        .addUser(BOB.asString(), BOB_PASSWORD)

      requestSpecification = baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
        .build
    }

    @Test
    def shouldReturnDefaultValuesWhenEmailRecoveryNotConfigured(): Unit = {
      val defaultMaxEmailRecoveryPerRequest = "5"
      val defaultRestorationHorizon = "15 days"

      `given`()
      .when()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .get("/session")
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .body("capabilities", hasKey("com:linagora:params:jmap:messages:vault"))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'", hasKey("maxEmailRecoveryPerRequest"))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'.maxEmailRecoveryPerRequest", equalTo(defaultMaxEmailRecoveryPerRequest))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'", hasKey("restorationHorizon"))
        .body("capabilities.'com:linagora:params:jmap:messages:vault'.restorationHorizon", equalTo(defaultRestorationHorizon))
    }
  }
}