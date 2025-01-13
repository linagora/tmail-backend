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

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.common.PublicAssetGetMethodContract.{CREATION_REQUEST, IDENTITY_ID}
import com.linagora.tmail.james.common.probe.PublicAssetProbe
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.{ImageContentType, PublicAssetCreationRequest, PublicAssetIdFactory}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{IdentityId, Size}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.ContentType
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object PublicAssetGetMethodContract {
  val CONTENT_TYPE: ContentType = ContentType.of("image/png")
  val IMAGE_CONTENT_TYPE: ImageContentType = ImageContentType.from(CONTENT_TYPE).toOption.get
  val ASSET_CONTENT: Array[Byte] = Array[Byte](1, 2, 3)
  val SIZE: Size = Size.sanitizeSize(ASSET_CONTENT.length)
  val IDENTITY_ID = IdentityId.generate
  val IDENTITY_IDS: Seq[IdentityId] = Seq(IDENTITY_ID)
  val CREATION_REQUEST: PublicAssetCreationRequest = PublicAssetCreationRequest(
    size = SIZE,
    contentType = IMAGE_CONTENT_TYPE,
    content = () => new ByteArrayInputStream(ASSET_CONTENT),
    identityIds = IDENTITY_IDS)
}

trait PublicAssetGetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def missingPublicAssetCapabilityShouldFail(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core"],
           |	"methodCalls": [
           |		[
           |			"PublicAsset/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"error",
           |			{
           |				"type": "unknownMethod",
           |				"description": "Missing capability(ies): com:linagora:params:jmap:public:assets"
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin))


  @Test
  def getShouldReturnEmptyAssetsByDefault(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |	"methodCalls": [
           |		[
           |			"PublicAsset/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "PublicAsset/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |        "list": [],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def fetchNullIdsShouldReturnAllAssets(server: GuiceJamesServer): Unit = {
    val publicAsset = server.getProbe(classOf[PublicAssetProbe]).create(BOB, CREATION_REQUEST)
    val publicAsset2 = server.getProbe(classOf[PublicAssetProbe]).create(BOB, CREATION_REQUEST)

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |	"methodCalls": [
           |		[
           |			"PublicAsset/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "PublicAsset/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |                  {
           |                      "id": "${publicAsset.id.value}",
           |                      "publicURI": "${publicAsset.publicURI.value}",
           |                      "size": 3,
           |                      "contentType": "image/png",
           |                      "identityIds": { "${IDENTITY_ID.id}": true }
           |                  },
           |                  {
           |                      "id": "${publicAsset2.id.value}",
           |                      "publicURI": "${publicAsset2.publicURI.value}",
           |                      "size": 3,
           |                      "contentType": "image/png",
           |                      "identityIds": { "${IDENTITY_ID.id}": true }
           |                  }
           |              ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def fetchIdsShouldReturnSpecificAssets(server: GuiceJamesServer): Unit = {
    val publicAsset = server.getProbe(classOf[PublicAssetProbe]).create(BOB, CREATION_REQUEST)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "ids": [ "${publicAsset.id.value}" ]
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "PublicAsset/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |                  {
           |                      "id": "${publicAsset.id.value}",
           |                      "publicURI": "${publicAsset.publicURI.value}",
           |                      "size": 3,
           |                      "contentType": "image/png",
           |                      "identityIds": { "${IDENTITY_ID.id}": true }
           |                  }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val publicAsset = server.getProbe(classOf[PublicAssetProbe]).create(BOB, CREATION_REQUEST)
    val nonExistedAssetId = PublicAssetIdFactory.generate()

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "ids": [
           |                  "${publicAsset.id.value}",
           |                  "${nonExistedAssetId.value}",
           |                  "notFound"
           |        ]
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "PublicAsset/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |                  {
           |                      "id": "${publicAsset.id.value}",
           |                      "publicURI": "${publicAsset.publicURI.value}",
           |                      "size": 3,
           |                      "contentType": "image/png",
           |                      "identityIds": { "${IDENTITY_ID.id}": true }
           |                  }
           |              ],
           |        "notFound": [ "notFound", "${nonExistedAssetId.value}" ]
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def shouldFailWhenWrongAccountId(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |	"methodCalls": [
           |		[
           |			"PublicAsset/get",
           |			{
           |				"accountId": "unknownAccountId",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["error", {
           |      "type": "accountNotFound"
           |    }, "c1"]
           |  ]
           |}""".stripMargin))
}
