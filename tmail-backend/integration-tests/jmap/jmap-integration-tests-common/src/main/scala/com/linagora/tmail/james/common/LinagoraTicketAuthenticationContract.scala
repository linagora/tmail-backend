package com.linagora.tmail.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, `with`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.jmap.core.JmapRfc8621Configuration.{UPLOAD_LIMIT_DEFAULT, URL_PREFIX_DEFAULT, WEBSOCKET_URL_PREFIX_DEFAULT}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_BASIC_AUTH_HEADER, BOB_PASSWORD, DOMAIN, baseRequestSpecBuilder, getHeadersWith}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

import scala.jdk.CollectionConverters._

object LinagoraTicketAuthenticationContract {
  def jmapConfiguration(): JmapRfc8621Configuration = JmapRfc8621Configuration(
    urlPrefixString = URL_PREFIX_DEFAULT,
    websocketPrefixString = WEBSOCKET_URL_PREFIX_DEFAULT,
    maxUploadSize = UPLOAD_LIMIT_DEFAULT,
    dynamicJmapPrefixResolutionEnabled = true,
    authenticationStrategies = Some(List("JWTAuthenticationStrategy", "BasicAuthenticationStrategy",
      "com.linagora.tmail.james.jmap.ticket.TicketAuthenticationStrategy").asJava))
}

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

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def sessionShouldAdvertiseTicketEndpoints(): Unit = {
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
      .inPath("capabilities.com:linagora:params:jmap:ws:ticket")
      .isEqualTo("""{
          |  "generationEndpoint":"http://localhost/jmap/ws/ticket",
          |  "revocationEndpoint":"http://localhost/jmap/ws/ticket"
          |}""".stripMargin)
  }
  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def customPrefixCanBeUsed(): Unit = {
    val sessionJson: String = `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .header("X-JMAP-PREFIX","http://custom")
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(sessionJson)
      .inPath("capabilities.com:linagora:params:jmap:ws:ticket")
      .isEqualTo("""{
          |  "generationEndpoint":"http://custom/jmap/ws/ticket",
          |  "revocationEndpoint":"http://custom/jmap/ws/ticket"
          |}""".stripMargin)
  }
}
