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

import io.restassured.RestAssured.requestSpecification
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.receiveMessageInTimespan
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.jdk.CollectionConverters._

trait LinagoraFilterStateChangeTest {

  def generateMailboxIdForUser(): String
  def generateAccountIdAsString(): String

  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

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
  @Tag(CategoryTags.BASIC_FEATURE)
  def pushShouldSupportFilterTypeNameAndFilterStateWhenDataTypesAreFilterTypeName(server: GuiceJamesServer): Unit = {
    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Filter"]
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |	"@type": "Request",
                 |	"requestId": "req-36",
                 |	"using": ["com:linagora:params:jmap:filter"],
                 |	"methodCalls": [
                 |		["Filter/set", {
                 |			"accountId": "$generateAccountIdAsString",
                 |			"update": {
                 |				"singleton": [{
                 |					"id": "1",
                 |					"name": "My first rule",
                 |					"condition": {
                 |						"field": "subject",
                 |						"comparator": "contains",
                 |						"value": "question"
                 |					},
                 |					"action": {
                 |						"appendIn": {
                 |							"mailboxIds": ["$generateMailboxIdForUser"]
                 |						}
                 |					}
                 |				}]
                 |			}
                 |		}, "c1"]
                 |	]
                 |}""".stripMargin))

            ws.receiveMessageInTimespan()
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val filterStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Filter":"0"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2)
      .contains(filterStateChange)
  }

  @Test
  def pushShouldSupportFilterTypeNameAndFilterStateWhenDataTypesAreNull(server: GuiceJamesServer): Unit = {
    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": null
                |}""".stripMargin))

            Thread.sleep(100)

            ws.send(WebSocketFrame.text(
              s"""{
                 |	"@type": "Request",
                 |	"requestId": "req-36",
                 |	"using": ["com:linagora:params:jmap:filter"],
                 |	"methodCalls": [
                 |		["Filter/set", {
                 |			"accountId": "$generateAccountIdAsString",
                 |			"update": {
                 |				"singleton": [{
                 |					"id": "1",
                 |					"name": "My first rule",
                 |					"condition": {
                 |						"field": "subject",
                 |						"comparator": "contains",
                 |						"value": "question"
                 |					},
                 |					"action": {
                 |						"appendIn": {
                 |							"mailboxIds": ["$generateMailboxIdForUser"]
                 |						}
                 |					}
                 |				}]
                 |			}
                 |		}, "c1"]
                 |	]
                 |}""".stripMargin))

            ws.receiveMessageInTimespan()
        })
        .send(backend)
        .body

    Thread.sleep(100)

    val filterStateChange: String = s"""{"@type":"StateChange","changed":{"$ACCOUNT_ID":{"Filter":"0"}}}"""

    assertThat(response.toOption.get.asJava)
      .hasSize(2)
      .contains(filterStateChange)
  }

  private def authenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .header("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }
}
