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

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import com.linagora.tmail.james.common.LinagoraKeystoreGetMethodContract.{PGP_KEY_ARMORED, PGP_KEY_ARMORED2, PGP_KEY_ID, PGP_KEY_ID2}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object LinagoraKeystoreGetMethodContract {
  case class TestContext(bobUsername: Username) {
    val bobAccountId: String = accountId(bobUsername)
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private val PGP_KEY: Array[Byte] = ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg.pub")
    .readAllBytes()
  private val PGP_KEY2: Array[Byte] = ClassLoader.getSystemClassLoader
    .getResourceAsStream("gpg2.pub")
    .readAllBytes()

  private val PGP_KEY_ARMORED: String = new String(PGP_KEY, StandardCharsets.UTF_8)
    .replace("\n", "\\n")
  private val PGP_KEY_ARMORED2: String = new String(PGP_KEY2, StandardCharsets.UTF_8)
    .replace("\n", "\\n")

  private val PGP_KEY_ID: String = "3BA423385C8C80D453D7E6F95BF4E866E9CC6FA2"
  private val PGP_KEY_ID2: String = "12522CF961A95474431BADD676E1BC47187D6CEF"
}

trait LinagoraKeystoreGetMethodContract {
  def bobUsername: Username = LinagoraKeystoreGetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = LinagoraKeystoreGetMethodContract.currentContext.get().bobAccountId

  @BeforeEach
  def setUp(server : GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    LinagoraKeystoreGetMethodContract.currentContext.set(LinagoraKeystoreGetMethodContract.TestContext(bob))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(bobUsername.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build()
  }

  @Test
  def keystoreGetAllShouldSucceed(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$bobAccountId",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        },
                     |        "K88": {
                     |          "key": "$PGP_KEY_ARMORED2"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId",
                     |      "ids": null
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

    assertThatJson(response)
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Keystore/set", {
         |      "accountId": "$bobAccountId",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        },
         |        "K88": {
         |          "id": "$PGP_KEY_ID2"
         |        }
         |      }
         |    }, "c1"],
         |    ["Keystore/get", {
         |      "accountId": "$bobAccountId",
         |      "state": "${INSTANCE.value}",
         |      "notFound": [],
         |      "list": [
         |        {
         |          "id": "$PGP_KEY_ID",
         |          "key": "$PGP_KEY_ARMORED"
         |        },
         |        {
         |          "id": "$PGP_KEY_ID2",
         |          "key": "$PGP_KEY_ARMORED2"
         |        }
         |      ]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def keystoreGetShouldSucceed(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$bobAccountId",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        },
                     |        "K88": {
                     |          "key": "$PGP_KEY_ARMORED2"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId",
                     |      "ids": [
                     |        "$PGP_KEY_ID"
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

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Keystore/set", {
         |      "accountId": "$bobAccountId",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        },
         |        "K88": {
         |          "id": "$PGP_KEY_ID2"
         |        }
         |      }
         |    }, "c1"],
         |    ["Keystore/get", {
         |      "accountId": "$bobAccountId",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id": "$PGP_KEY_ID",
         |          "key": "$PGP_KEY_ARMORED"
         |        }
         |      ],
         |      "notFound": []
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreGetShouldSupportBackReference(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$bobAccountId",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        },
                     |        "K88": {
                     |          "key": "$PGP_KEY_ARMORED2"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId",
                     |      "ids": [
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

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["Keystore/set", {
         |      "accountId": "$bobAccountId",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        },
         |        "K88": {
         |          "id": "$PGP_KEY_ID2"
         |        }
         |      }
         |    }, "c1"],
         |    ["Keystore/get", {
         |      "accountId": "$bobAccountId",
         |      "state": "${INSTANCE.value}",
         |      "notFound": [],
         |      "list": [
         |        {
         |          "id": "$PGP_KEY_ID",
         |          "key": "$PGP_KEY_ARMORED"
         |        }
         |      ]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreGetShouldReturnEmptyWhenNoKey(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId",
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
         |    ["Keystore/get", {
         |      "accountId": "$bobAccountId",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": []
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreGetShouldReturnNotFoundyWhenUnknownId(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
                     |  "methodCalls": [
                     |    ["Keystore/set", {
                     |      "accountId": "$bobAccountId",
                     |      "create": {
                     |        "K87": {
                     |          "key": "$PGP_KEY_ARMORED"
                     |        }
                     |      }
                     |    }, "c1"],
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId",
                     |      "ids": [
                     |        "$PGP_KEY_ID2"
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
         |      "accountId": "$bobAccountId",
         |      "created": {
         |        "K87": {
         |          "id": "$PGP_KEY_ID"
         |        }
         |      }
         |    }, "c1"],
         |    ["Keystore/get", {
         |      "accountId": "$bobAccountId",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound":["12522CF961A95474431BADD676E1BC47187D6CEF"]
         |    }, "c2"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def keystoreGetShouldFailWhenMissingOneCapability(): Unit = {
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core"],
                     |  "methodCalls": [
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId"
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
  def keystoreGetShouldFailWhenMissingAllCapabilities(): Unit = {
    val request = s"""{
                     |  "using": [],
                     |  "methodCalls": [
                     |    ["Keystore/get", {
                     |      "accountId": "$bobAccountId"
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
}
