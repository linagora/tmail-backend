package com.linagora.tmail.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait LinagoraSupportEmailGetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def shouldReturnSupportEmailWhenConfiguredCorrectly(): Unit = {
    val request: String =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "com:linagora:params:jmap:contact:support"],
         |  "methodCalls": [[
         |    "SupportEmail/get",
         |    {},
         |    "c1"]]
         |}""".stripMargin

    val response = `given`
      .body(request)
      .when
      .get
      .`then`
      .log().all()
      .contentType(JSON)
      .extract
      .body
      .asString

    println(response)
  }

  @Test
  def shouldReturnErrorWhenSupportEmailNotConfigured(): Unit = {

  }

  @Test
  def shouldReturnErrorWhenSupportEmailIsInvalid(): Unit = {

  }

}
