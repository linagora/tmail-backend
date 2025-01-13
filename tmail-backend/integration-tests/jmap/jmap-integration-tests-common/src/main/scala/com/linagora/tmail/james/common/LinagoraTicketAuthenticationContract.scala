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

import java.net.URI

import com.linagora.tmail.james.common.LinagoraTicketAuthenticationContract.{WEB_SOCKET_ECHO_REQUEST, WEB_SOCKET_ECHO_RESPONSE}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.core.JmapRfc8621Configuration.{UPLOAD_LIMIT_DEFAULT, URL_PREFIX_DEFAULT, WEBSOCKET_URL_PREFIX_DEFAULT}
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_BASIC_AUTH_HEADER, BOB_PASSWORD, DOMAIN, baseRequestSpecBuilder, getHeadersWith}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import sttp.ws.WebSocketFrame
import sttp.ws.WebSocketFrame.Text

import scala.jdk.CollectionConverters._

object LinagoraTicketAuthenticationContract {
  def jmapConfiguration(): JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = URL_PREFIX_DEFAULT,
    websocketPrefixString = WEBSOCKET_URL_PREFIX_DEFAULT,
    maxUploadSize = UPLOAD_LIMIT_DEFAULT,
    dynamicJmapPrefixResolutionEnabled = true,
    authenticationStrategies = Some(List("JWTAuthenticationStrategy", "BasicAuthenticationStrategy",
      "com.linagora.tmail.james.jmap.ticket.TicketAuthenticationStrategy").asJava))

  val WEB_SOCKET_ECHO_REQUEST: Text = WebSocketFrame.text(
    """{
      |  "@type": "Request",
      |  "id": "req-36",
      |  "using": [ "urn:ietf:params:jmap:core"],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin)

  val WEB_SOCKET_ECHO_RESPONSE: String =
    """{
      |  "@type":"Response",
      |  "requestId":"req-36",
      |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
      |  "methodResponses":[
      |    ["Core/echo",
      |      {
      |        "arg1":"arg1data",
      |        "arg2":"arg2data"
      |      },"c1"]
      |  ]
      |}""".stripMargin
}

trait LinagoraTicketAuthenticationContract {
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme)
      .build
  }

  @Test
  def ticketShouldGrantAuth(server: GuiceJamesServer): Unit = {
    val ticket: String = getTicket

    val response: Either[String, String] = sendWebSocketRequest(server, ticket)

    assertThatJson(response.toOption.get)
      .isEqualTo(WEB_SOCKET_ECHO_RESPONSE)
  }

  @Test
  def ticketShouldBeSingleUse(server: GuiceJamesServer): Unit = {
    val ticket: String = getTicket

    sendWebSocketRequest(server, ticket)

    assertThatThrownBy(() => sendWebSocketRequest(server, ticket))
      .hasRootCauseMessage("Expected HTTP 101 response but was '401 Unauthorized'")
  }

  @Test
  def ticketShouldBeRevocable(server: GuiceJamesServer): Unit = {
    val ticket: String = getTicket

    `with`()
      .basePath(s"/jmap/ws/ticket/$ticket")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .delete()

    assertThatThrownBy(() => sendWebSocketRequest(server, ticket))
      .hasRootCauseMessage("Expected HTTP 101 response but was '401 Unauthorized'")
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def sessionShouldAdvertiseTicketEndpoints(): Unit = {
    val sessionJson: String = `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(sessionJson)
      .inPath("capabilities.com:linagora:params:jmap:ws:ticket")
      .isEqualTo("""{
          |  "generationEndpoint":"http://localhost/jmap/ws/ticket",
          |  "revocationEndpoint":"http://localhost/jmap/ws/ticket"
          |}""".stripMargin)
  }
  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def customPrefixCanBeUsed(): Unit = {
    val sessionJson: String = `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header("X-JMAP-PREFIX","http://custom")
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(sessionJson)
      .inPath("capabilities.com:linagora:params:jmap:ws:ticket")
      .isEqualTo("""{
          |  "generationEndpoint":"http://custom/jmap/ws/ticket",
          |  "revocationEndpoint":"http://custom/jmap/ws/ticket"
          |}""".stripMargin)
  }

  private def sendWebSocketRequest(server: GuiceJamesServer, ticket: String) =
    authenticatedRequest(server, ticket)
      .response(asWebSocket[Identity, String] {
        ws =>
          ws.send(WEB_SOCKET_ECHO_REQUEST)

          ws.receive()
            .map { case t: Text => t.payload }
      })
      .send(backend)
      .body

  private def authenticatedRequest(server: GuiceJamesServer, ticketValue: String): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")).addParam("ticket", ticketValue))
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }

  private def getTicket: String = `given`()
      .basePath("/jmap/ws/ticket")
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("")
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .jsonPath()
      .getString("value")
}
