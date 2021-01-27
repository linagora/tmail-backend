package com.linagora.openpaas.james.common

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
import org.junit.jupiter.api.{BeforeEach, Test}

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
}
