package com.linagora.tmail.james.common

import java.util.UUID

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait LongLivedTokenGetContract {
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
  def getAllShouldSucceed(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "My android device"
         |        },
         |        "K39": {
         |          "deviceId": "My IOS device"
         |        }
         |      }
         |    }, "c1"],
         |    ["LongLivedToken/get", {
         |        "accountId": "$ACCOUNT_ID",
         |        "ids": null
         |      }, "c2"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "created": {
         |        "K38": {
         |          "id": "$${json-unit.ignore}",
         |          "token": "$${json-unit.ignore}"
         |        },
         |        "K39": {
         |          "id": "$${json-unit.ignore}",
         |          "token": "$${json-unit.ignore}"
         |        }
         |      }
         |    }, "c1"],
         |    ["LongLivedToken/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id": "$${json-unit.ignore}",
         |          "deviceId": "My android device"
         |        },
         |        {
         |          "id": "$${json-unit.ignore}",
         |          "deviceId": "My IOS device"
         |        }
         |      ],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def getShouldSucceed(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
                     |  "methodCalls": [
                     |    ["LongLivedToken/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K38": {
                     |          "deviceId": "My android device"
                     |        },
                     |        "K39": {
                     |          "deviceId": "My IOS device"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["LongLivedToken/get", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "ids": [
                     |        "#K39"
                     |      ]
                     |    }, "c2"]
                     |  ]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "created": {
         |        "K38": {
         |          "id": "$${json-unit.ignore}",
         |          "token": "$${json-unit.ignore}"
         |        },
         |        "K39": {
         |          "id": "$${json-unit.ignore}",
         |          "token": "$${json-unit.ignore}"
         |        }
         |      }
         |    }, "c1"],
         |    ["LongLivedToken/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id": "$${json-unit.ignore}",
         |          "deviceId": "My IOS device"
         |        }
         |      ],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnEmptyWhenNoKey(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
                     |  "methodCalls": [
                     |    ["LongLivedToken/get", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "ids": []
                     |    }, "c1"]
                     |  ]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["LongLivedToken/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": []
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnEmptyWhenUnknownId(): Unit = {
    val id = UUID.randomUUID().toString
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
                     |  "methodCalls": [
                     |    ["LongLivedToken/get", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "ids": [ "$id" ]
                     |    }, "c2"]
                     |  ]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request).log().all()
    .when()
      .post().prettyPeek()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["LongLivedToken/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": ["$id"]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def getShouldFailWhenMissingOneCapability(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core"],
                     |  "methodCalls": [
                     |    ["LongLivedToken/get", {
                     |      "accountId": "$ACCOUNT_ID"
                     |    }, "c1"]
                     |  ]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState":"${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): com:linagora:params:long:lived:token"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def getShouldFailWhenMissingAllCapabilities(): Unit = {
    val request = s"""{
                     |  "using": [],
                     |  "methodCalls": [
                     |    ["LongLivedToken/get", {
                     |      "accountId": "$ACCOUNT_ID"
                     |    }, "c1"]
                     |  ]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState":"${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:long:lived:token"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }
}
