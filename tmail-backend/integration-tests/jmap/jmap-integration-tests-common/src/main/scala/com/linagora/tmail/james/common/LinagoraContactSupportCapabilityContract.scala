package com.linagora.tmail.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers
import org.hamcrest.Matchers.{equalTo, hasKey}
import org.junit.jupiter.api.{AfterEach, Test}

trait LinagoraContactSupportCapabilityContract {

  def startJmapServer(overrideJmapProperties: Map[String, Object]): GuiceJamesServer

  def stopJmapServer(): Unit

  private def setUpJmapServer(jmapConfiguration: Map[String, Object]): Unit = {
    val server = startJmapServer(jmapConfiguration)
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @AfterEach
  def afterEach(): Unit = {
    stopJmapServer()
  }

  @Test
  def shouldReturnSupportMailAddressWhenConfigured(): Unit = {
    setUpJmapServer(Map("support.mail.address" -> "support@linagora.abc",
      "support.httpLink" -> null))

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:contact:support"))
      .body("capabilities.'com:linagora:params:jmap:contact:support'", hasKey("supportMailAddress"))
      .body("capabilities.'com:linagora:params:jmap:contact:support'.supportMailAddress", equalTo("support@linagora.abc"))
  }

  @Test
  def shouldNotReturnSupportMailAddressWhenItIsNotConfigured(): Unit = {
    setUpJmapServer(Map("support.mail.address" -> null))

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:contact:support"))
      .body("capabilities.'com:linagora:params:jmap:contact:support'", Matchers.not(hasKey("supportMailAddress")))
  }

  @Test
  def shouldReturnSupportHttpLinkWhenConfigured(): Unit = {
    setUpJmapServer(Map("support.httpLink" -> "http://linagora.abc/support",
      "support.mail.address" -> null))

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:contact:support"))
      .body("capabilities.'com:linagora:params:jmap:contact:support'", hasKey("httpLink"))
      .body("capabilities.'com:linagora:params:jmap:contact:support'.httpLink", equalTo("http://linagora.abc/support"))
  }

  @Test
  def shouldNotReturnSupportHttpLinkWhenItIsNotConfigured(): Unit = {
    setUpJmapServer(Map("support.httpLink" -> null))

    `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:contact:support"))
      .body("capabilities.'com:linagora:params:jmap:contact:support'", Matchers.not(hasKey("httpLink")))
  }
}