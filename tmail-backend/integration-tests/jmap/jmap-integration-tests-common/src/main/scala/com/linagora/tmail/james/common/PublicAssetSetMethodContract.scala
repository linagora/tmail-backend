package com.linagora.tmail.james.common

import java.util.UUID

import com.linagora.tmail.james.common.PublicAssetSetMethodContract.UploadResponse
import com.linagora.tmail.james.common.probe.PublicAssetProbe
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetId
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.ContentType.MimeType
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, Json}

import scala.jdk.CollectionConverters._

object PublicAssetSetMethodContract {
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
    val identityIdsAsJson: String = Json.stringify(Json.arr(identityIds)).replace("[[", "[").replace("]]", "]")

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
           |            "identityIds": $identityIdsAsJson
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
           |            "identityIds": ["$invalidIdentityId"]
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
           |        "description": "Invalid identityId: invalid-identity-id###"
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
           |            "identityIds": ["$notFoundIdentityId"]
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
    val identityIdsAsJson: String = Json.stringify(Json.arr(identityIds)).replace("[[", "[").replace("]]", "]")
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
           |            "identityIds": $identityIdsAsJson
           |          },
           |          "4f30": {
           |            "blobId": "${notFoundBlobId}"
           |          },
           |          "4f31": {
           |            "blobId": "${uploadResponse2.blobId}",
           |            "identityIds": ["$notFoundIdentityId"]
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
           |            "identityIds": ["${identityIds.head}"]
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
           |            "identityIds": ["${identityIds.head}"]
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
  def updateShouldReturnNotUpdatedWhenIdentityIdDoesNotExists():Unit = {
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
           |            "identityIds": ["$notFoundIdentityId"]
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
  def updateShouldReturnNotUpdatedWhenIdentityIdsContainOneDoesNotExist(): Unit = {
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
           |            "identityIds": ["$notFoundIdentityId", "$identityId"]
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
  def updateShouldReturnNotUpdatedWhenMissingIdentityIdsProperty():Unit = {
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
           |        "description": "Missing '/identityIds'"
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
           |            "identityIds": ["${identityIds.head}"]
           |          },
           |          "$publicAssetId2": {
           |            "identityIds": ["${identityIds.head}"]
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
           |            "identityIds": ["$notFoundIdentityId", "$identityId"]
           |          },
           |          "$publicAssetId2": {
           |            "identityIds": ["$identityId"]
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
           |            "identityIds": ["${identityIds.head}"]
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
  def updateShouldSuccessWhenIdentityIdsIsEmpty(server: GuiceJamesServer): Unit = {
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
           |            "identityIds": ["${identityId}"]
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
           |            "identityIds": []
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
}