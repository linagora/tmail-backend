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

import java.io.ByteArrayInputStream

import com.google.common.net.HttpHeaders.{CACHE_CONTROL, CONTENT_LENGTH}
import com.linagora.tmail.james.common.PublicAssetRouteContract.{ASSET_CONTENT, IMAGE_CONTENT_TYPE, SIZE}
import com.linagora.tmail.james.common.probe.JmapGuicePublicAssetProbe
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.{ImageContentType, PublicAssetCreationRequest}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.{SC_NOT_FOUND, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.model.Size
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.ContentType
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.{BeforeEach, Test}

object PublicAssetRouteContract {
  val IMAGE_CONTENT_TYPE: ImageContentType = ImageContentType.from(ContentType.of("image/png")).toOption.get
  val ASSET_CONTENT: Array[Byte] = ClassLoaderUtils.getSystemResourceAsByteArray("publicasset/tmail-logo.png")
  val SIZE: Size = Size.sanitizeSize(ASSET_CONTENT.length)
}

trait PublicAssetRouteContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, BOB_PASSWORD)
      .addUser(CEDRIC.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .setUrlEncodingEnabled(false)
      .build
  }

  @Test
  def succeedCase(server: GuiceJamesServer): Unit = {
    val creationRequest: PublicAssetCreationRequest = PublicAssetCreationRequest(
      size = SIZE,
      contentType = IMAGE_CONTENT_TYPE,
      content = () => new ByteArrayInputStream(ASSET_CONTENT),
      identityIds = Seq.empty)

    val publicAssetStorage = server.getProbe(classOf[JmapGuicePublicAssetProbe])
      .addPublicAsset(BOB, creationRequest)

    val responseAsByteArray = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(publicAssetStorage.publicURI.value.getPath)
    .`then`
      .statusCode(SC_OK)
      .contentType(IMAGE_CONTENT_TYPE.value)
      .header(CONTENT_LENGTH, SIZE.toString())
      .header(CACHE_CONTROL, "immutable, max-age=31536000")
      .extract()
      .body()
      .asByteArray()

    assertThat(responseAsByteArray).isEqualTo(ASSET_CONTENT)
  }

  @Test
  def shouldReturnNotFoundWhenNonExistingUsernameAndNonExistingAssetId(): Unit = {
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/publicAsset/whateverUsername/whateverAssetId")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .contentType(JSON)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The public asset could not be found"))
  }

  @Test
  def shouldReturnNotFoundWhenExistingUsernameAndNonExistingAssetId(): Unit = {
    `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/publicAsset/${BOB.asString()}/whateverAssetId")
    .`then`
      .statusCode(SC_NOT_FOUND)
      .contentType(JSON)
      .body("status", equalTo(404))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("The public asset could not be found"))
  }

  @Test
  def shouldSupportPreflightRequest(): Unit = {
    `given`()
    .when()
      .basePath("/publicAsset/whateverUsername/whateverAssetId")
      .options()
    .`then`()
      .statusCode(SC_OK)
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
      .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
      .header("Access-Control-Max-Age", "86400")
  }

}
