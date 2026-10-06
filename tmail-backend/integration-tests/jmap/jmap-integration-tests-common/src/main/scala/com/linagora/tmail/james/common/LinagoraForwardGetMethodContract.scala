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
import com.linagora.tmail.james.common.LinagoraForwardGetMethodContract.{basePath, webAdminApi}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.{SC_NO_CONTENT, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.{DataProbeImpl, WebAdminGuiceProbe}
import org.apache.james.webadmin.WebAdminUtils
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object LinagoraForwardGetMethodContract {
  case class TestContext(bobUsername: Username, andreUsername: Username, cedricUsername: Username) {
    val bobAccountId: String = accountId(bobUsername)
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()

  private def bobUsername: Username = currentContext.get().bobUsername

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  private var webAdminApi: RequestSpecification = _
  private def basePath: String = s"/address/forwards/${bobUsername.asString}/targets"
}

trait LinagoraForwardGetMethodContract {
  def bobUsername: Username = LinagoraForwardGetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = LinagoraForwardGetMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = LinagoraForwardGetMethodContract.currentContext.get().andreUsername

  def cedricUsername: Username = LinagoraForwardGetMethodContract.currentContext.get().cedricUsername

  @BeforeEach
  def setUp(server : GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    val cedric = Username.fromLocalPartWithDomain(s"cedric$uniqueSuffix", DOMAIN)
    LinagoraForwardGetMethodContract.currentContext.set(LinagoraForwardGetMethodContract.TestContext(bob, andre, cedric))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(bobUsername.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build()

    webAdminApi = WebAdminUtils.buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .setBasePath(basePath)
      .build()
  }

  @Test
  def forwardGetShouldReturnEmptyListByDefault(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true,
         |          "forwards": []
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def forwardGetShouldSucceedWhenOneForward(): Unit = {
    `given`
      .spec(webAdminApi)
    .when()
      .put(andreUsername.asString)
    .`then`()
      .statusCode(SC_NO_CONTENT)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": false,
         |          "forwards": ["${andreUsername.asString}"]
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnMultipleForwards(): Unit = {
    `given`
      .spec(webAdminApi)
    .when()
      .put(andreUsername.asString)
    .`then`()
      .statusCode(SC_NO_CONTENT)

    `given`
      .spec(webAdminApi)
    .when()
      .put(cedricUsername.asString)
    .`then`()
      .statusCode(SC_NO_CONTENT)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": false,
         |          "forwards": ["${andreUsername.asString}", "${cedricUsername.asString}"]
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnLocalCopyTrueAndEmptyForwardListWhenOnlyForwardToHimself(): Unit = {
    `given`
      .spec(webAdminApi)
    .when()
      .put(bobUsername.asString)
    .`then`()
      .statusCode(SC_NO_CONTENT)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true,
         |          "forwards": []
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnLocalCopyTrueAndNotInListWhenForwardToHimselfAndOthers(): Unit = {
    `given`
      .spec(webAdminApi)
    .when()
      .put(bobUsername.asString)
    .`then`()
      .statusCode(SC_NO_CONTENT)

    `given`
      .spec(webAdminApi)
    .when()
      .put(andreUsername.asString)
    .`then`()
      .statusCode(SC_NO_CONTENT)

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true,
         |          "forwards": ["${andreUsername.asString}"]
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldFailWhenWrongAccountId(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "unknownAccountId",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

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
  def forwardGetShouldFailWhenOmittingOneCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): com:linagora:params:jmap:forward"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldFailWhenOmittingAllCapabilities(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:forward"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnValidResponseWhenSingletonId(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": ["singleton"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true,
         |          "forwards": []
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnNotFoundWhenIdNotSingleton(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": ["random"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": ["random"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnSingletonAndNotFoundIds(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": ["random1", "singleton", "random2"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .when(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true,
         |          "forwards": []
         |        }
         |      ],
         |      "notFound": ["random1", "random2"]
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnEmptyListWhenEmptyIdsArray(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": []
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldFailWhenEmptyId(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": [""]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "error",
           |      {
           |        "type": "invalidArguments"
           |      },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnAllPropertiesWhenNull(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null,
               |      "properties": null
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true,
         |          "forwards": []
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnIdWhenNoPropertiesRequested(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null,
               |      "properties": []
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton"
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnOnlyRequestedProperties(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null,
               |      "properties": ["id", "localCopy"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldAlwaysReturnIdEvenIfNotRequestedInProperties(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null,
               |      "properties": ["localCopy"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Forward/get",
         |    {
         |      "accountId": "${bobAccountId}",
         |      "state": "${INSTANCE.value}",
         |      "list": [
         |        {
         |          "id":"singleton",
         |          "localCopy": true
         |        }
         |      ],
         |      "notFound": []
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def forwardGetShouldReturnInvalidArgumentsErrorWhenInvalidProperty(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "com:linagora:params:jmap:forward"],
               |  "methodCalls": [[
               |    "Forward/get",
               |    {
               |      "accountId": "${bobAccountId}",
               |      "ids": null,
               |      "properties": ["invalidProperty"]
               |    },
               |    "c1"]]
               |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "invalidArguments",
         |      "description": "The following properties [invalidProperty] do not exist."
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }
}
