package com.linagora.tmail.james.common

import java.nio.charset.StandardCharsets

import com.linagora.tmail.encrypted.KeyId
import com.linagora.tmail.james.common.LinagoraKeystoreSetMethodContract.{PGP_KEY, PGP_KEY_ARMORED, PGP_KEY_ID}
import com.linagora.tmail.james.common.probe.JmapGuiceKeystoreManagerProbe
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ALICE_ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_BASIC_AUTH_HEADER, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder, getHeadersWith}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object LinagoraKeystoreSetMethodContract {
  private val PGP_KEY: Array[Byte] = ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg.pub")
    .readAllBytes()

  private val PGP_KEY_ARMORED: String = new String(PGP_KEY, StandardCharsets.UTF_8)
    .replace("\n", "\\n")

  private val PGP_KEY_ID: String = "3BA423385C8C80D453D7E6F95BF4E866E9CC6FA2"
}

trait LinagoraKeystoreSetMethodContract {

  @BeforeEach
  def setUp(server : GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build()
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def keystoreSetCreateShouldSucceedWithValidKey(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
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
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldStoreCorrectlyAValidKey(server: GuiceJamesServer): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
                     |    }, "c1"]
                     |  ]
                     |}""".stripMargin

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)

    assertThat(server.getProbe(classOf[JmapGuiceKeystoreManagerProbe])
      .retrieveKey(BOB, KeyId(PGP_KEY_ID))
      .get()
      .key)
      .isEqualTo(PGP_KEY)
  }

  @Test
  def keystoreSetShouldReturnUnknownMethodWhenOmittingItsCapability(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
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
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): com:linagora:params:jmap:pgp"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateWithWrongAccountIdShouldFail(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ALICE_ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
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
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldReturnNotCreatedWhenMissingKey(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {}
                     |      }
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
           |  "methodResponses": [[
           |    "Keystore/set", {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "notCreated": {
           |        "K87": {
           |          "type": "invalidArguments",
           |          "description": "Missing '/key' property"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldReturnNotCreatedWhenKeyIsEmpty(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": ""
                     |        }
                     |      }
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
         |    ["Keystore/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "K87": {
         |          "type": "invalidArguments",
         |          "description": "java.lang.IllegalArgumentException: Can't find encryption key in key ring."
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldReturnNotCreatedWhenKeyIsWrong(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "wrong_key"
                     |        }
                     |      }
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
         |    ["Keystore/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "K87": {
         |          "type": "invalidArguments",
         |          "description": "java.lang.IllegalArgumentException: Can't find encryption key in key ring."
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldReturnNotCreatedWhenUnknownParam(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED",
                     |          "unknown": "blabla"
                     |        }
                     |      }
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
         |    ["Keystore/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "K87": {
         |          "type": "invalidArguments",
         |          "description": "Some unknown properties were specified",
         |          "properties": ["unknown"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldReturnNotCreatedWhenServerSetParam(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED",
                     |          "id": "blabla"
                     |        }
                     |      }
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
         |    ["Keystore/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "notCreated": {
         |        "K87": {
         |          "type": "invalidArguments",
         |          "description": "Some server-set properties were specified",
         |          "properties": ["id"]
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetCreateShouldBeIdempotent(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        },
                     |        "K88": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
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

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        },
         |        "K88": {
         |          "id": "$PGP_KEY_ID"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetDestroyShouldSucceed(server: GuiceJamesServer): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "destroy": [
                     |        "$PGP_KEY_ID"
                     |      ]
                     |    }, "c2"]
                     |	]
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
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |        "created": {
         |          "K87": {
         |            "id": "$PGP_KEY_ID"
         |          }
         |        }
         |    }, "c1"],
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "destroyed": [
         |        "$PGP_KEY_ID"
         |      ]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[JmapGuiceKeystoreManagerProbe])
      .retrieveKey(BOB, KeyId(PGP_KEY_ID)))
      .isEmpty

  }

  @Test
  def keystoreSetDestroyWithBackReferenceShouldSucceed(server: GuiceJamesServer): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "destroy": [
                     |        "#K87"
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
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        }
         |      }
         |    }, "c1"],
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "destroyed": [
         |        "$PGP_KEY_ID"
         |      ]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[JmapGuiceKeystoreManagerProbe])
      .retrieveKey(BOB, KeyId(PGP_KEY_ID)))
      .isEmpty
  }

  @Test
  def keystoreSetDestroyWithUnknownKeyIdShouldSucceed(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ACCOUNT_ID",
                     |      "destroy": [
                     |        "unknown"
                     |      ]
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
         |    ["Keystore/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "destroyed": [
         |        "unknown"
         |      ]
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetDestroyWithWrongAccountIdShouldFail(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ALICE_ACCOUNT_ID",
                     |      "destroy": [
                     |        "$PGP_KEY_ID"
                     |      ]
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
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetDestroyShouldFailWhenMissingOneCapability(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [
         |    ["Keystore/set", {
         |      "accountId": "$ALICE_ACCOUNT_ID",
         |      "destroy": [
         |        "$PGP_KEY_ID"
         |      ]
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
         |      "description": "Missing capability(ies): com:linagora:params:jmap:pgp"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreSetDestroyShouldFailWhenMissingAllCapabilities(): Unit = {
    val request = s"""{
                     |  "using": [],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$ALICE_ACCOUNT_ID",
                     |      "destroy": [
                     |        "$PGP_KEY_ID"
                     |      ]
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:pgp"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def sessionShouldAdvertiseCapability(): Unit = {
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
      .inPath("capabilities.com:linagora:params:jmap:pgp")
      .isEqualTo("{}")
  }

  @Test
  def keystoreSetShouldRejectFromDelegatedAccount(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val bobAccountId = ACCOUNT_ID
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["Keystore/set", {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "K87": {
         |          "key": "$PGP_KEY_ARMORED"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"error",
           |	{
           |		"type": "forbidden",
           |		"description": "Access to other accounts settings is forbidden"
           |	},
           |	"c1"
           |]""".stripMargin)
  }
}
