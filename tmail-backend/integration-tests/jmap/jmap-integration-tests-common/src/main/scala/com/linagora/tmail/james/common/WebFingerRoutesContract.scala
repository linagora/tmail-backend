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

import java.net.URL

import com.google.inject.Module
import com.linagora.tmail.james.jmap.WebFingerConfiguration
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_BAD_REQUEST, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, baseRequestSpecBuilder}
import org.junit.jupiter.api.{BeforeEach, Test}

object WebFingerRoutesContract {
  val MODULE: Module = binder => binder.bind(classOf[WebFingerConfiguration])
    .toInstance(new WebFingerConfiguration(Some(new URL("https://auth.linagora.com/auth/realms/jmap"))))
}

trait WebFingerRoutesContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme)
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def shouldRejectNoRelQueryParameter(): Unit = {
    val response: String = `given`()
    .when()
      .basePath(".well-known/webfinger")
      .queryParam("resource", "https://jmap.linagora.com")
      .get()
    .`then`()
      .statusCode(SC_BAD_REQUEST)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 400,
           |    "detail": "'rel' query parameter is compulsory"
           |}""".stripMargin)
  }

  @Test
  def shouldRejectBadRelQueryParameter(): Unit = {
    val response: String = `given`()
    .when()
      .basePath(".well-known/webfinger")
      .queryParam("resource", "https://jmap.linagora.com")
      .queryParam("rel", "https://bad")
      .get()
    .`then`()
      .statusCode(SC_BAD_REQUEST)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 400,
           |    "detail": "'rel' supports only 'http://openid.net/specs/connect/1.0/issuer' (URL encoded: http%3A%2F%2Fopenid.net%2Fspecs%2Fconnect%2F1.0%2Fissuer)"
           |}""".stripMargin)
  }

  @Test
  def shouldRejectNoResource(): Unit = {
    val response: String = `given`()
    .when()
      .basePath(".well-known/webfinger")
      .queryParam("rel", "https://bad")
      .get()
    .`then`()
      .statusCode(SC_BAD_REQUEST)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 400,
           |    "detail": "'resource' query parameter is compulsory"
           |}""".stripMargin)
  }


  @Test
  def shouldReturnOpenIdURL(): Unit = {
    val response: String = `given`()
    .when()
      .basePath(".well-known/webfinger")
      .queryParam("resource", "https://jmap.linagora.com")
      .queryParam("rel", "http://openid.net/specs/connect/1.0/issuer")
      .get()
    .`then`()
      .statusCode(SC_OK)
      .contentType("application/jrd+json")
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "subject": "https://jmap.linagora.com",
           |    "links": [
           |        {
           |            "rel": "http://openid.net/specs/connect/1.0/issuer",
           |            "href": "https://auth.linagora.com/auth/realms/jmap"
           |        }
           |    ]
           |}
           |""".stripMargin)
  }
}
