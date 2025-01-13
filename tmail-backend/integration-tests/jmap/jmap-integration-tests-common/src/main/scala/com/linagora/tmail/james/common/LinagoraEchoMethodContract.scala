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

import com.linagora.tmail.james.jmap.firebase.FirebasePushClient
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.mock

object LinagoraEchoMethodContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait LinagoraEchoMethodContract {

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
  def echoMethodShouldRespondOKWithRFC8621VersionAndSupportedMethod(): Unit = {
    val request =
      """{
        |  "using": [
        |    "urn:ietf:params:jmap:core", "com:linagora:params:jmap:echo"
        |  ],
        |  "methodCalls": [
        |    [
        |      "Linagora/echo",
        |      {
        |        "arg1": "arg1data",
        |        "arg2": "arg2data"
        |      },
        |      "c1"
        |    ]
        |  ]
        |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
          |  "sessionState": "${SESSION_STATE.value}",
          |  "methodResponses": [
          |    [
          |      "Linagora/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
  }

  @Test
  def echoMethodShouldRespondWithRFC8621VersionAndUnsupportedMethod(): Unit = {
    val request =
      """{
        |  "using": [
        |    "urn:ietf:params:jmap:core", "com:linagora:params:jmap:echo"
        |  ],
        |  "methodCalls": [
        |    [
        |      "Linagora/echo",
        |      {
        |        "arg1": "arg1data",
        |        "arg2": "arg2data"
        |      },
        |      "c1"
        |    ],
        |    [
        |      "error",
        |      {
        |        "type": "unknownMethod"
        |      },
        |      "notsupport"
        |    ]
        |  ]
        |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    [
           |      "Linagora/echo",
           |      {
           |        "arg1": "arg1data",
           |        "arg2": "arg2data"
           |      },
           |      "c1"
           |    ],
           |    [
           |      "error",
           |      {
           |        "type": "unknownMethod"
           |      },
           |      "notsupport"
           |    ]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def echoMethodShouldReturnUnknownMethodWhenMissingCoreCapability(): Unit = {
    val request =
      """{
        |  "using": [
        |    "com:linagora:params:jmap:echo"
        |  ],
        |  "methodCalls": [
        |    [
        |      "Linagora/echo",
        |      {
        |        "arg1": "arg1data",
        |        "arg2": "arg2data"
        |      },
        |      "c1"
        |    ]
        |  ]
        |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def echoMethodShouldReturnUnknownMethodWhenMissingLinagoraEchoCapability(): Unit = {
    val request =
      """{
        |  "using": [
        |    "urn:ietf:params:jmap:core"
        |  ],
        |  "methodCalls": [
        |    [
        |      "Linagora/echo",
        |      {
        |        "arg1": "arg1data",
        |        "arg2": "arg2data"
        |      },
        |      "c1"
        |    ]
        |  ]
        |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
         |      "description": "Missing capability(ies): com:linagora:params:jmap:echo"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def shouldReturnFirebaseCapabilityWhenFirebaseEnabled(): Unit = {
   val response: String = `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThat(response).contains("\"com:linagora:params:jmap:firebase:push\":{\"apiKey\":\"key123\",\"appId\":\"firebase123\",\"messagingSenderId\":\"sender123\",\"projectId\":\"project123\",\"databaseUrl\":\"http://database.com\",\"storageBucket\":\"bucket123\",\"authDomain\":\"domain123\",\"vapidPublicKey\":\"vapidkey123\"}")
  }

  @Test
  def shouldReturnTeamMailboxesCapability(): Unit = {
   val response: String = `given`()
    .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThat(response).contains("\"com:linagora:params:jmap:team:mailboxes\":{}")
  }

  @Test
  def shouldReturnMessagesVaultCapability(): Unit =
      `given`()
    .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:messages:vault"))

  @Test
  def shouldReturnPublicAssetsCapability(): Unit =
      `given`()
    .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:public:assets"))
      .body("capabilities.'com:linagora:params:jmap:public:assets'", hasKey("publicAssetTotalSize"))

  @Test
  def shouldReturnAutoCompleteCapability(): Unit =
      `given`()
    .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:contact:autocomplete"))
      .body("capabilities.'com:linagora:params:jmap:contact:autocomplete'", hasKey("minInputLength"))
}
