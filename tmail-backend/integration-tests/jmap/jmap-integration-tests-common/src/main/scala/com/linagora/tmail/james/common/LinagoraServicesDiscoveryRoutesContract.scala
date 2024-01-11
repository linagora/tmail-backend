package com.linagora.tmail.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.{BeforeEach, Test}

trait LinagoraServicesDiscoveryRoutesContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def serviceDiscoveryShouldSucceedWhenUserIsAuthenticated(): Unit = {
    val response: String = `given`()
      .auth().basic(BOB.asString(), BOB_PASSWORD)
    .when()
      .basePath(".well-known/linagora-ecosystem")
      .get()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |	"linShareApiUrl": "https://linshare.linagora.com/linshare/webservice",
           |	"linToApiUrl": "https://linto.ai/demo",
           |	"linToApiKey": "apiKey",
           |	"twakeApiUrl": "https://api.twake.app"
           |}""".stripMargin)
  }

  @Test
  def serviceDiscoveryShouldSucceedWhenDelegateeUser(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addAuthorizedUser(BOB, ANDRE)

    val response: String = `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .when()
        .basePath(".well-known/linagora-ecosystem")
        .get()
      .`then`()
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body()
        .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |	"linShareApiUrl": "https://linshare.linagora.com/linshare/webservice",
           |	"linToApiUrl": "https://linto.ai/demo",
           |	"linToApiKey": "apiKey",
           |	"twakeApiUrl": "https://api.twake.app"
           |}""".stripMargin)
  }

  @Test
  def serviceDiscoveryShouldFailWhenUserIsNotAuthenticated(): Unit = {
    `given`()
      .auth().none()
    .when()
      .basePath(".well-known/linagora-ecosystem")
      .get()
    .`then`()
      .statusCode(SC_UNAUTHORIZED)
      .contentType(JSON)
      .header("WWW-Authenticate", "Basic realm=\"simple\", Bearer realm=\"JWT\"")
      .body("status", equalTo(401))
      .body("type", equalTo("about:blank"))
      .body("detail", equalTo("No valid authentication methods provided"))
  }

  @Test
  def shouldSupportPreflightRequest(): Unit = {
    `given`()
    .when()
      .basePath(".well-known/linagora-ecosystem")
      .options()
    .`then`()
      .statusCode(SC_OK)
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT")
      .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
      .header("Access-Control-Max-Age", "86400")
  }
}
