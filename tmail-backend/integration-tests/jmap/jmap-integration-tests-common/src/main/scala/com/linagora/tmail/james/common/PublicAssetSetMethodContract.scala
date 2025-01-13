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

import java.util.UUID

import com.linagora.tmail.james.common.PublicAssetSetMethodContract.UploadResponse
import com.linagora.tmail.james.common.probe.PublicAssetProbe
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetId
import com.linagora.tmail.james.jmap.{JMAPExtensionConfiguration, PublicAssetTotalSizeLimit}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{IdentityId, IdentityName}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.IdentityProbe
import org.apache.james.jmap.rfc8621.contract.IdentitySetContract.IDENTITY_CREATION_REQUEST
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.ContentType.MimeType
import org.apache.james.util.Size
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.{contains, containsString, hasItem, hasKey, is, notNullValue}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import play.api.libs.json.{JsString, Json}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

object PublicAssetSetMethodContract {
  val CONFIGURATION: JMAPExtensionConfiguration = JMAPExtensionConfiguration(
    publicAssetTotalSizeLimit = PublicAssetTotalSizeLimit.of(Size.of(500L, Size.Unit.B)).get
  )

  case class UploadResponse(blobId: String, contentType: MimeType, size: Long)
}

trait PublicAssetSetMethodContract {
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
      .log().ifValidationFails()
  }

  private def uploadAsset(content: Array[Byte] = "Your asset content here".getBytes,
                          contentType: String = "image/png"): UploadResponse = {
    val uploadResponse: String = `given`()
      .basePath("")
      .contentType(contentType)
      .body(content)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .extract
      .body
      .asString

    UploadResponse(blobId = (Json.parse(uploadResponse) \ "blobId").as[String],
      contentType = org.apache.james.mailbox.model.ContentType.of((Json.parse(uploadResponse) \ "type").as[String]).mimeType(),
      size = (Json.parse(uploadResponse) \ "size").as[Long])
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def createValidRequestShouldReturnSuccessResponse(): Unit = {
    val uploadResponse: UploadResponse = uploadAsset()

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
         |  "methodCalls": [
         |    [
         |      "PublicAsset/set", {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "4f29": {
         |            "blobId": "${uploadResponse.blobId}"
         |          }
         |        }
         |      }, "0"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String = `given`()
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "PublicAsset/set",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "created": {
           |            "4f29": {
           |                "id": "$${json-unit.ignore}",
           |                "size": ${uploadResponse.size},
           |                "contentType": "${uploadResponse.contentType.asString()}",
           |                "publicURI": "$${json-unit.ignore}"
           |            }
           |        }
           |    },
           |    "0"
           |]""".stripMargin)
  }

  @Test
  def createShouldReturnNotCreatedWhenRequestMissingBlobId(): Unit =
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", JsonMatchers.jsonEquals(
        s"""[
           |    "PublicAsset/set",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "notCreated": {
           |            "4f29": {
           |                "type": "invalidArguments",
           |                "description": "Missing '/blobId' property"
           |            }
           |        }
           |    },
           |    "0"
           |]""".stripMargin))

  @Test
  def createShouldReturnNotCreatedWhenBlobIdIsNotImageContentType(): Unit = {
    // Given upload an blob that is not a picture content type
    val uploadResponse: UploadResponse = uploadAsset(contentType = "text/plain")

    // When create a public asset with this blobId
    // Then the request should return notCreated
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}"
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "type": "invalidArguments",
           |        "description": "Invalid content type: Predicate failed: 'text/plain' is not a valid image content type. A valid image content type should start with 'image/'."
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldReturnNotCreatedWhenBlobIdDoesNotExist(): Unit = {
    // Given an not found blobId
    val notFoundBlobId: String = "uploads-ce192a10-1992-11ef-b9f4-39749479be62"
    // When create a public asset with this blobId
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "$notFoundBlobId"
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    // Then the request should return notCreated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "type": "invalidArguments",
           |        "description": "BlobId not found: $notFoundBlobId"
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldSupportIdentityIdsProperty(): Unit = {
    // Given an uploaded asset
    val uploadResponse: UploadResponse = uploadAsset()
    // And some identityIds
    val identityIds: Seq[String] = getIdentityIds()

    val identityIdMap: Map[String, Boolean] = identityIds.map(identityId => identityId -> true).toMap
    val identityIdMapAsJson: String = Json.stringify(Json.toJson(identityIdMap))

    // When create a public asset with identityIds property
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}",
           |            "identityIds": $identityIdMapAsJson
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    // Then the request should return created
    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "id": "$${json-unit.ignore}",
           |        "size": ${uploadResponse.size},
           |        "contentType": "${uploadResponse.contentType.asString()}",
           |        "publicURI": "$${json-unit.ignore}"
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldReturnNotCreatedWhenIdentityIdInvalid(): Unit = {
    // Given an uploaded asset
    val uploadResponse: UploadResponse = uploadAsset()
    // And an invalid identityId
    val invalidIdentityId: String = "invalid-identity-id###"

    // When create a public asset with invalid identityId
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}",
           |            "identityIds": { "$invalidIdentityId": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    // Then the request should return notCreated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "type": "invalidArguments",
           |        "description": "'/identityIds/invalid-identity-id###' property is not valid: Invalid UUID string: invalid-identity-id###"
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldReturnNotCreatedWhenIdentityIdDoesNotExist(): Unit = {
    // Given an uploaded asset
    val uploadResponse: UploadResponse = uploadAsset()
    // And an not found identityId
    val notFoundIdentityId: String = IdentityId.generate.serialize

    // When create a public asset with not found identityId
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}",
           |            "identityIds": { "$notFoundIdentityId": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    // Then the request should return notCreated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "type": "invalidArguments",
           |        "description": "IdentityId not found: $notFoundIdentityId"
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldWorkCorrectWhenMixCases(): Unit = {
    val uploadResponse: UploadResponse = uploadAsset()
    val uploadResponse2: UploadResponse = uploadAsset(content = "Content2".getBytes)
    val identityIds: Seq[String] = getIdentityIds()
    val identityIdMapAsJson: String = Json.stringify(Json.toJson(identityIds.map(identityId => identityId -> true).toMap))
    val notFoundIdentityId: String = IdentityId.generate.serialize
    val notFoundBlobId: String = "uploads-ce192a10-1992-11ef-b9f4-39749479be62"

    // When create a public asset with mix cases
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}",
           |            "identityIds": $identityIdMapAsJson
           |          },
           |          "4f30": {
           |            "blobId": "${notFoundBlobId}"
           |          },
           |          "4f31": {
           |            "blobId": "${uploadResponse2.blobId}",
           |            "identityIds": {"$notFoundIdentityId": true}
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "PublicAsset/set",
           |    {
           |        "accountId": "$ACCOUNT_ID",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "created": {
           |            "4f29": {
           |                "id": "$${json-unit.ignore}",
           |                "publicURI": "$${json-unit.ignore}",
           |                "size": ${uploadResponse.size},
           |                "contentType": "${uploadResponse.contentType.asString()}"
           |            }
           |        },
           |        "notCreated": {
           |            "4f30": {
           |                "type": "invalidArguments",
           |                "description": "BlobId not found: $notFoundBlobId"
           |            },
           |            "4f31": {
           |                "type": "invalidArguments",
           |                "description": "IdentityId not found: $notFoundIdentityId"
           |            }
           |        }
           |    },
           |    "0"
           |]""".stripMargin)
  }

  @Test
  def createShouldReturnFailWhenMissingCapability(): Unit = {
    // Given an uploaded asset
    val uploadResponse: UploadResponse = uploadAsset()

    // When create a public asset with missing capability
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}"
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    // Then the request should return notCreated
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "unknownMethod",
           |        "description": "Missing capability(ies): com:linagora:params:jmap:public:assets"
           |    },
           |    "0"
           |]""".stripMargin)
  }

  @Test
  def createShouldFailWhenInvalidPropertyInCreationRequest(): Unit = {
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "invalid1": "invalid-blob-id"
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    // Then the request should return notCreated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "type": "invalidArguments",
           |        "description": "Some unknown properties were specified",
           |        "properties": [
           |            "invalid1"
           |        ]
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldFailWhenIdentityIdValueIsFalse(): Unit = {
    val uploadResponse: UploadResponse = uploadAsset()
    val identityId: String = getIdentityIds().head

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
         |  "methodCalls": [
         |    [
         |      "PublicAsset/set", {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "4f29": {
         |            "blobId": "${uploadResponse.blobId}",
         |            "identityIds": { "$identityId": false }
         |          }
         |        }
         |      }, "0"
         |    ]
         |  ]
         |}""".stripMargin

    `given`()
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .body("methodResponses[0][1].notCreated.4f29.type", is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.4f29.description", containsString("value can only be true"))
  }

  @Test
  def creationSetShouldSupportBackReferences(): Unit = {
    val uploadResponse: UploadResponse = uploadAsset()

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
         |  "methodCalls": [
         |    [
         |      "PublicAsset/set", {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "clientId1": {
         |            "blobId": "${uploadResponse.blobId}"
         |          }
         |        }
         |      }, "c1"
         |    ],
         |    [
         |      "Core/echo",
         |      { "arg1": "#clientId1" },
         |      "c2"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String = `given`()
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses")
      .isEqualTo(
        s"""[
           |    [
           |        "PublicAsset/set",
           |        {
           |            "accountId": "$ACCOUNT_ID",
           |            "oldState": "$${json-unit.ignore}",
           |            "newState": "$${json-unit.ignore}",
           |            "created": {
           |                "clientId1": {
           |                    "id": "$${json-unit.ignore}",
           |                    "publicURI": "$${json-unit.ignore}",
           |                    "size": ${uploadResponse.size},
           |                    "contentType": "$${json-unit.ignore}"
           |                }
           |            }
           |        },
           |        "c1"
           |    ],
           |    [
           |        "Core/echo",
           |        {
           |            "arg1": "$${json-unit.ignore}"
           |        },
           |        "c2"
           |    ]
           |]""".stripMargin)

    val taskId: String = (((Json.parse(response) \\ "methodResponses")
      .head \\ "created")
      .head \\ "id")
      .head.asInstanceOf[JsString].value

    val backReferenceValue: String = ((Json.parse(response) \\ "methodResponses")
      .head \\ "arg1")
      .head.asInstanceOf[JsString].value

    assertThat(taskId)
      .isEqualTo(backReferenceValue)
  }

  @Test
  def updateShouldReturnSuccessResponseWhenValidRequest(): Unit = {
    // give public asset Id
    val publicAssetId: String = createPublicAssetId()
    val identityIds: Seq[String] = getIdentityIds()

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { "${identityIds.head}": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "updated": {
           |      "$publicAssetId": null
           |    }
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenPublicAssetIdDoesNotExists(): Unit = {
    // Given an not found public asset Id
    val notFoundPublicAssetId: String = "ce192a10-1992-11ef-b9f4-39749479be62"
    val identityIds: Seq[String] = getIdentityIds()

    // When update the public asset with not found public asset Id
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$notFoundPublicAssetId": {
           |            "identityIds": { "${identityIds.head}": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "notUpdated": {
           |      "$notFoundPublicAssetId": {
           |        "type": "invalidArguments",
           |        "description": "Public asset not found: $notFoundPublicAssetId"
           |      }
           |    }
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenResetIdentityIdDoesNotExists():Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()
    val notFoundIdentityId: String = IdentityId.generate.serialize

    // When update the public asset with not found identityId
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { "${notFoundIdentityId}": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "notUpdated": {
           |      "$publicAssetId": {
           |        "type": "invalidArguments",
           |        "description": "IdentityId not found: $notFoundIdentityId"
           |      }
           |    }
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenResetIdentityIdsContainOneDoesNotExist(): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()
    val notFoundIdentityId: String = IdentityId.generate.serialize
    val identityId: String = getIdentityIds().head

    // When update the public asset with not found identityId
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { "${notFoundIdentityId}": true,  "$identityId": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "notUpdated": {
           |      "$publicAssetId": {
           |        "type": "invalidArguments",
           |        "description": "IdentityId not found: $notFoundIdentityId"
           |      }
           |    }
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenEmptyPatchUpdateRequest():Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()

    // When update the public asset with missing identityIds property
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "notUpdated": {
           |      "$publicAssetId": {
           |        "type": "invalidArguments",
           |        "description": "Cannot update identityIds with empty request"
           |      }
           |    }
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def updateShouldSupportSeveralAssetIdInRequest(): Unit = {
    val publicAssetId1: String = createPublicAssetId()
    val publicAssetId2: String = createPublicAssetId()
    val identityIds: Seq[String] = getIdentityIds()

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId1": {
           |            "identityIds": { "${identityIds.head}": true }
           |          },
           |          "$publicAssetId2": {
           |            "identityIds": { "${identityIds.head}": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(
        s"""{"${publicAssetId1}":null,"${publicAssetId2}":null}""")
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateShouldSuccessWhenMixCases(): Unit = {
    val publicAssetId1: String = createPublicAssetId()
    val publicAssetId2: String = createPublicAssetId()
    val notFoundIdentityId: String = IdentityId.generate.serialize
    val identityId: String = getIdentityIds().head

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId1": {
           |            "identityIds": { "$identityId": true, "$notFoundIdentityId": true}
           |          },
           |          "$publicAssetId2": {
           |            "identityIds": { "$identityId": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(
        s"""{"$publicAssetId2":null}""")
    assertThatJson(response)
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "$publicAssetId1": {
           |    "type": "invalidArguments",
           |    "description": "IdentityId not found: $notFoundIdentityId"
           |  }
           |}""".stripMargin)
  }

  @Test
  def updateSuccessShouldStorageNewIdentityIds(server: GuiceJamesServer): Unit = {
    // give public asset Id
    val publicAssetId: String = createPublicAssetId()
    val identityIds: Seq[String] = getIdentityIds()

    // verify the public asset has no identityIds
    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get)
      .identityIds.asJava).hasSize(0)

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds":  { "${identityIds.head}": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response).inPath("methodResponses[0][1].updated").isEqualTo(s"""{"${publicAssetId}":null}""")

    // Then new IdentityIds was stored in the public asset
    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get)
      .identityIds
      .map(_.id.toString)
      .asJava).containsExactlyInAnyOrder(identityIds.head)
  }

  @Test
  def updateShouldSuccessWhenResetIdentityIdsIsEmpty(server: GuiceJamesServer): Unit = {
    // give public asset Id with identityId
    val uploadResponse: UploadResponse = uploadAsset(content = UUID.randomUUID().toString.getBytes)
    val identityId: String = getIdentityIds().head
    val publicAssetId: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}",
           |            "identityIds": { "${identityId}": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")

    // verify the public asset has identityIds
    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get)
      .identityIds.asJava).hasSize(1)

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": {}
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response).inPath("methodResponses[0][1].updated").isEqualTo(s"""{"${publicAssetId}":null}""")

    // Then new IdentityIds was stored in the public asset
    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get)
      .identityIds
      .asJava).hasSize(0)
  }

  @Test
  def updateShouldFailWhenUpdateRequestIsNotJson(server: GuiceJamesServer): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()

    // when update the public asset with invalid update request (not json)
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": "not-json"
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return fail
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "invalidArguments",
           |        "description": "'/update/$publicAssetId' property is not valid: error.expected.jsobject"
           |    },
           |    "0"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenInvalidResetIdentityIdsRequest(server: GuiceJamesServer): Unit = {
    // Given public asset Id with identityId
    val identityId: String = getIdentityIds().head
    val publicAssetId: String = createPublicAssetIdWithIdentityId(Seq(identityId))

    // when update the public asset with invalid identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { "invalid-identity-id": true }
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "$publicAssetId": {
           |    "type": "invalidArguments",
           |    "description": "$${json-unit.ignore}"
           |  }
           |}""".stripMargin)
  }

  @Test
  def updateShouldReturnUpdatedWhenAddPartialValidate(server: GuiceJamesServer): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()
    val identityId1: String = getIdentityIds().head

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds/${identityId1}": true
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return updated
    assertThatJson(response)
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(
        s"""{"$publicAssetId":null}""")

    // Then the public asset should have identityId1
    assertThat(getIdentityIdsByUsernameAndPublicAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get, server))
      .containsExactlyInAnyOrder(identityId1)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenAddPartialWithNotFoundIdentityId(server: GuiceJamesServer): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()
    val notFoundIdentityId: String = IdentityId.generate.serialize

    // when update the public asset with identityIds
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds/$notFoundIdentityId": true
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "$publicAssetId": {
           |    "type": "invalidArguments",
           |    "description": "IdentityId not found: $notFoundIdentityId"
           |  }
           |}""".stripMargin)
  }

  @Test
  def updateShouldReturnUpdatedWhenRemovePartialValidate(server: GuiceJamesServer): Unit = {
    // Given public asset Id with identityId1
    val identityId1: String = getIdentityIds().head
    val publicAssetId: String = createPublicAssetIdWithIdentityId(Seq(identityId1))

    // when update the public asset with identityId1
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds/${identityId1}": null
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return updated
    assertThatJson(response)
      .inPath("methodResponses[0][1].updated")
      .isEqualTo(
        s"""{"$publicAssetId":null}""")

    // Then the public asset should not have identityId1
    assertThat(getIdentityIdsByUsernameAndPublicAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get, server))
      .doesNotContain(identityId1)
  }

  @Test
  def updateShouldReturnUpdatedWhenRemovePartialIdempotent(server: GuiceJamesServer): Unit = {
    // Given public asset Id with empty identityIds
    val identityId1: String = getIdentityIds().head
    val publicAssetId: String = createPublicAssetId()

    // when update the public asset with identityIds
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds/${identityId1}": null
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].updated", hasKey(publicAssetId))

    assertThat(getIdentityIdsByUsernameAndPublicAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get, server))
      .hasSize(0)
  }

  @Test
  def updateShouldSupportMixAddAndRemovePartialAtSameTime(server: GuiceJamesServer): Unit = {
    // Given +3 identityIds
    val identityProbe = server.getProbe(classOf[IdentityProbe])
    val identityId1: String = SMono(identityProbe.save(BOB, IDENTITY_CREATION_REQUEST)).block().id.serialize
    val identityId2: String = SMono(identityProbe.save(BOB, IDENTITY_CREATION_REQUEST.copy(name = Some(IdentityName("Bob (custom address)2"))))).block().id.serialize
    val identityId3: String = SMono(identityProbe.save(BOB, IDENTITY_CREATION_REQUEST.copy(name = Some(IdentityName("Bob (custom address)3"))))).block().id.serialize
    assertThat(getIdentityIds().size).isGreaterThan(3)

    // Given public asset Id with identityId1 + identityId2
    val publicAssetId: String = createPublicAssetIdWithIdentityId(Seq(identityId1, identityId2))

    // when update the public asset with add identityId3 and remove identityId1
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds/${identityId1}": null,
           |            "identityIds/${identityId3}": true
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].updated", hasKey(publicAssetId))

    // Then new IdentityIds should be is identityId2 + identityId3
    assertThat(getIdentityIdsByUsernameAndPublicAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get, server))
      .containsExactlyInAnyOrder(identityId2, identityId3)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenTryResetAndUpdatePartialAtSameTime(server: GuiceJamesServer): Unit = {
    // Given public asset Id with identityId1
    val identityProbe = server.getProbe(classOf[IdentityProbe])
    val identityId1: String = SMono(identityProbe.save(BOB, IDENTITY_CREATION_REQUEST)).block().id.serialize
    val publicAssetId: String = createPublicAssetIdWithIdentityId(Seq(identityId1))

    // when update the public asset with reset and update partial at same time
    val response: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { "${identityId1}": true },
           |            "identityIds/${identityId1}": null
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "$publicAssetId": {
           |    "type": "invalidArguments",
           |    "description": "Cannot reset identityIds and add/remove identityIds at the same time"
           |  }
           |}""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUnknownProperty(server: GuiceJamesServer): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()

    // when update the public asset with unknown property
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$publicAssetId": {
           |            "unknownProperty": true
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notUpdated
    assertThatJson(response)
      .inPath("methodResponses[0][1].notUpdated")
      .isEqualTo(
        s"""{
           |  "$publicAssetId": {
           |    "type": "invalidArguments",
           |    "description": "'/unknownProperty' property is not valid: Unknown property"
           |  }
           |}""".stripMargin)
  }

  @Test
  def setUpdateShouldNotSupportDelegationWhenNotDelegatedUser(server: GuiceJamesServer): Unit = {
    // Given public asset Id (of Bob)
    val publicAssetId: String = createPublicAssetIdWithIdentityId(getIdentityIds())
    val bobAccountID = ACCOUNT_ID

    // when update the public asset with not delegated user
    val response: String = `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$bobAccountID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { }
           |          }
           |        }
           |      }, "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return error
    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "$${json-unit.ignore}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)

    // Then the public asset should not be updated
    assertThat(getIdentityIdsByUsernameAndPublicAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get, server).size())
      .isGreaterThan(0)
  }

  @Test
  def setUpdateShouldSupportDelegationWhenDelegatedUser(server: GuiceJamesServer): Unit = {
    // Given public asset Id (of Bob)
    val publicAssetId: String = createPublicAssetIdWithIdentityId(getIdentityIds())
    val bobAccountID = ACCOUNT_ID
    // Given delegation
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    // when update the public asset with delegated user
    `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$bobAccountID",
           |        "update": {
           |          "$publicAssetId": {
           |            "identityIds": { }
           |          }
           |        }
           |      }, "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].updated", hasKey(publicAssetId))

    // Then the public asset should be updated
    assertThat(getIdentityIdsByUsernameAndPublicAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get, server).size())
      .isEqualTo(0)
  }

  private def getIdentityIdsByUsernameAndPublicAssetId(username: Username, publicAssetId: PublicAssetId, server: GuiceJamesServer): java.util.List[String] =
    server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(username, publicAssetId)
      .identityIds
      .map(_.id.toString)
      .asJava

  private def getIdentityIds(): Seq[String] =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
           |  "methodCalls": [[
           |    "Identity/get",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "ids": null
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .jsonPath()
      .getList("methodResponses[0][1].list.id", classOf[String])
      .asScala.toSeq

  private def createPublicAssetId() : String = {
    val uploadResponse: UploadResponse = uploadAsset(content = UUID.randomUUID().toString.getBytes)
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}"
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
  .when()
      .post
  .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")
  }

  @Test
  def setDestroyShouldReturnDestroyedWhenValidRequest(): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()

    // When destroy the public asset
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "destroy": ["$publicAssetId"]
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "destroyed": ["$publicAssetId"]
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def setDestroyShouldDeletePublicAssetWhenValidRequest(server: GuiceJamesServer): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()

    // When destroy the public asset
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "destroy": ["$publicAssetId"]
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].destroyed", hasItem(publicAssetId))

    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get))
      .isNull()
  }

  @Test
  def setDestroyShouldReturnNotDestroyedWhenInvalidPublicAssetId(): Unit = {
    // Given an invalid public asset Id
    val invalidPublicAssetId: String = "@InvalidId"

    // When destroy the public asset with invalid public asset Id
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "destroy": ["$invalidPublicAssetId"]
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notDestroyed
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "notDestroyed": {
           |      "$invalidPublicAssetId": {
           |        "type": "invalidArguments",
           |        "description": "Invalid UUID string: $invalidPublicAssetId"
           |      }
           |    }
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def setDestroyShouldBeIdempotent(): Unit = {
    // Given public asset Id
    val publicAssetId: String = createPublicAssetId()

    // Destroy the public asset
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "destroy": ["$publicAssetId"]
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].destroyed", hasItem(publicAssetId))

    // When destroy the public asset again
    val response: String =  `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "destroy": ["$publicAssetId"]
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return destroyed
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "PublicAsset/set",
           |  {
           |    "accountId": "$ACCOUNT_ID",
           |    "oldState": "$${json-unit.ignore}",
           |    "newState": "$${json-unit.ignore}",
           |    "destroyed": ["$publicAssetId"]
           |  },
           |  "0"
           |]""".stripMargin)
  }

  @Test
  def setDestroyShouldNotSupportDelegationWhenNotDelegatedUser(server: GuiceJamesServer): Unit = {
    // Given public asset Id (of Bob)
    val publicAssetId: String = createPublicAssetId()
    val bobAccountID = ACCOUNT_ID

    // When destroy the public asset with not delegated user
    val response: String =  `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$bobAccountID",
           |        "destroy": ["$publicAssetId"]
           |      }, "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .body()
      .asString()

    // Then the request should return notDestroyed
    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "$${json-unit.ignore}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get))
      .isNotNull
  }

  @Test
  def setDestroyShouldSupportDelegationWhenDelegatedUser(server: GuiceJamesServer): Unit = {
    // Given public asset Id (of Bob)
    val publicAssetId: String = createPublicAssetId()
    val bobAccountID = ACCOUNT_ID

    // Given delegated user
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    // When destroy the public asset with delegated user
    // Then the request should return destroyed
    `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$bobAccountID",
           |        "destroy": ["$publicAssetId"]
           |      }, "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].destroyed", hasItem(publicAssetId))

    // Then the public asset was deleted
    assertThat(server.getProbe(classOf[PublicAssetProbe])
      .getByUsernameAndAssetId(BOB, PublicAssetId.fromString(publicAssetId).toOption.get))
      .isNull()
  }

  private def createPublicAssetIdWithIdentityId(identityIds: Seq[String]): String = {
    val identityIdMapAsJson: String = Json.stringify(Json.toJson(identityIds.map(identityId => identityId -> true).toMap))
    val uploadResponse: UploadResponse = uploadAsset(content = UUID.randomUUID().toString.getBytes)
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}",
           |            "identityIds": $identityIdMapAsJson
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id")
  }

  @Test
  def createShouldReturnFailWhenPublicAssetQuotaLimitIsExceededAndCanNotCleanUp(): Unit = {
    val content = "Your asset content here".repeat(20).getBytes
    val uploadResponse: UploadResponse = uploadAsset(content)
    val uploadResponse2: UploadResponse = uploadAsset(content)

    val identityIds: Seq[String] = getIdentityIds()
    val identityIdMap: Map[String, Boolean] = identityIds.map(identityId => identityId -> true).toMap
    val identityIdMapAsJson: String = Json.stringify(Json.toJson(identityIdMap))
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
         |  "methodCalls": [
         |    [
         |      "PublicAsset/set", {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "4f29": {
         |            "blobId": "${uploadResponse.blobId}",
         |            "identityIds": $identityIdMapAsJson
         |          }
         |        }
         |      }, "0"
         |    ]
         |  ]
         |}""".stripMargin

    `given`()
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val request2: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
         |  "methodCalls": [
         |    [
         |      "PublicAsset/set", {
         |        "accountId": "$ACCOUNT_ID",
         |        "create": {
         |          "4f29": {
         |            "blobId": "${uploadResponse2.blobId}",
         |            "identityIds": $identityIdMapAsJson
         |          }
         |        }
         |      }, "0"
         |    ]
         |  ]
         |}""".stripMargin

    val response: String = `given`()
      .body(request2)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "4f29": {
           |        "type": "overQuota",
           |        "description": "Exceeding public asset quota limit of 500 bytes"
           |    }
           |}""".stripMargin)
  }

  @Test
  def createShouldSuccessWhenPublicAssetQuotaLimitIsExceededAndCleanUpSucceed(): Unit = {
    val content = "Your asset content here".repeat(20).getBytes
    val uploadResponse: UploadResponse = uploadAsset(content)
    val uploadResponse2: UploadResponse = uploadAsset(content)

    // Given Create a public asset A, with no identityIds
    val publicAssetIdA: String = `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "4f29": {
           |            "blobId": "${uploadResponse.blobId}"
           |          }
           |        }
           |      }, "0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.4f29.id").toString

    // When Create a public asset B has PublicAssetQuota limit exceeded
    // Then the request should return created successfully
    `given`()
      .body( s"""{
                |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
                |  "methodCalls": [
                |    [
                |      "PublicAsset/set", {
                |        "accountId": "$ACCOUNT_ID",
                |        "create": {
                |          "4f29": {
                |            "blobId": "${uploadResponse2.blobId}"
                |          }
                |        }
                |      }, "0"
                |    ]
                |  ]
                |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .body("methodResponses[0][1].created.4f29.id", is(notNullValue()))

    // And the public asset A should be deleted
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:public:assets"],
           |  "methodCalls": [
           |    [
           |      "PublicAsset/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "ids": ["$publicAssetIdA"]
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notFound", is(contains(publicAssetIdA)))
  }

}