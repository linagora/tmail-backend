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
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers.{equalTo, hasKey}
import org.junit.jupiter.api.{AfterEach, Test}

trait CalendarEventSupportCapabilityContract {

  def startJmapServer(): GuiceJamesServer

  def startJmapServerWithCalendarSupport(): GuiceJamesServer

  def stopJmapServer(): Unit

  private def provisionTestResources(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @AfterEach
  def tearDown(): Unit = stopJmapServer()

  @Test
  def shouldEnableSupportFreeBusyQueryWhenCalDavIsConfigured(): Unit = {
    provisionTestResources(startJmapServerWithCalendarSupport())

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.'com:linagora:params:calendar:event'", hasKey("supportFreeBusyQuery"))
      .body("capabilities.'com:linagora:params:calendar:event'.supportFreeBusyQuery", equalTo(true))
  }

  @Test
  def shouldDisableSupportFreeBusyQueryWhenCalDavIsNotConfigured(): Unit = {
    provisionTestResources(startJmapServer())

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.'com:linagora:params:calendar:event'", hasKey("supportFreeBusyQuery"))
      .body("capabilities.'com:linagora:params:calendar:event'.supportFreeBusyQuery", equalTo(false))
  }

  @Test
  def shouldEnableSupportCounterQueryWhenCalDavIsConfigured(): Unit = {
    provisionTestResources(startJmapServerWithCalendarSupport())

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.'com:linagora:params:calendar:event'", hasKey("counterSupport"))
      .body("capabilities.'com:linagora:params:calendar:event'.counterSupport", equalTo(true))
  }

  @Test
  def shouldDisableSupportCounterQueryWhenCalDavIsNotConfigured(): Unit = {
    provisionTestResources(startJmapServer())

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities.'com:linagora:params:calendar:event'", hasKey("counterSupport"))
      .body("capabilities.'com:linagora:params:calendar:event'.counterSupport", equalTo(false))
  }
}