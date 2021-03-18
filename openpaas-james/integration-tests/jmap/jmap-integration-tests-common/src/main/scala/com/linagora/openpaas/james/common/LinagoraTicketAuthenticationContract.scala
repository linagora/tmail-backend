package com.linagora.openpaas.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_BASIC_AUTH_HEADER, BOB_PASSWORD, DOMAIN, baseRequestSpecBuilder, getHeadersWith}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait LinagoraTicketAuthenticationContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme)
      .build
  }

  @Test
  def ticketShouldGrantAuth(): Unit = {
    val ticket: String = `given`()
      .basePath("/jmap/ws/ticket")
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("")
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .jsonPath()
      .getString("value")

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
      .queryParam("ticket", ticket)
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
  def ticketShouldBeSingleUse(): Unit = {
    val ticket: String = `given`()
      .basePath("/jmap/ws/ticket")
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("")
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .jsonPath()
      .getString("value")

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

    `with`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .queryParam("ticket", ticket)
      .post()

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .queryParam("ticket", ticket)
    .when()
      .post()
    .`then`
      .statusCode(SC_UNAUTHORIZED)
  }

  @Test
  def ticketShouldBeRevocable(): Unit = {
    val ticket: String = `given`()
      .basePath("/jmap/ws/ticket")
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body("")
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .jsonPath()
      .getString("value")

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

    `with`()
      .basePath(s"/jmap/ws/ticket/$ticket")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .body(request)
      .delete()

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .queryParam("ticket", ticket)
    .when()
      .post()
    .`then`
      .statusCode(SC_UNAUTHORIZED)
  }
}
