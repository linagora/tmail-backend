package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.LinagoraShortLivedTokenRoutesContract.{BASE_PATH, DEVICE_ID}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.authentication.NoAuthScheme
import io.restassured.http.ContentType.JSON
import io.restassured.http.Header
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.{SC_INTERNAL_SERVER_ERROR, SC_OK, SC_UNAUTHORIZED}
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, AUTHORIZATION_HEADER, BOB, BOB_BASIC_AUTH_HEADER, BOB_PASSWORD, DOMAIN, ECHO_REQUEST_OBJECT, USER_TOKEN, baseRequestSpecBuilder, getHeadersWith}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import play.api.libs.json.{JsString, Json}

import java.util.stream.Stream

object LinagoraShortLivedTokenRoutesContract {
  val BASE_PATH: String = "/token"
  val DEVICE_ID: String = "samsung1"

  def unSupportAuthenHeaderStream : Stream[Arguments] = {
    Stream.of(
      Arguments.of(BOB_BASIC_AUTH_HEADER),
      Arguments.of(new Header(AUTHORIZATION_HEADER, s"Bearer $USER_TOKEN")))
  }
}

trait LinagoraShortLivedTokenRoutesContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(new NoAuthScheme)
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  private def createLongLivedToken(): String = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "$DEVICE_ID"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`()
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.K38.token")
  }

  private def buildBearerTokenHeader(authenticationToken: String): Header =
    new Header(AUTHORIZATION_HEADER, s"Bearer $authenticationToken")

  @Test
  def getTokenShouldReturnJWTTokenResponse(): Unit = {
    val longLivedToken: String = createLongLivedToken()

    val response: String = `given`()
      .header(buildBearerTokenHeader(longLivedToken))
      .queryParam("type", "shortLived")
      .queryParam("deviceId", DEVICE_ID)
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""
          |{
          |  "token" : "$${json-unit.ignore}",
          |  "expiresOn" : "$${json-unit.ignore}"
          |}""".stripMargin)
  }

  @ParameterizedTest
  @MethodSource(value = Array("unSupportAuthenHeaderStream"))
  def getTokenShouldNotAcceptOtherAuthenWithoutLongLivedToken(authenHeader: Header): Unit = {
    val response: String = `given`()
      .header(authenHeader)
      .queryParam("type", "shortLived")
      .queryParam("deviceId", DEVICE_ID)
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(HttpStatus.SC_UNAUTHORIZED)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 401,
           |    "detail": "No valid authentication methods provided"
           |}""".stripMargin)
  }

  @Test
  def jwtTokenResponseShouldGrantAuthToJmapEndpoint(): Unit = {
    val longLivedToken: String = createLongLivedToken()

    val jwtToken: String = `given`()
      .header(buildBearerTokenHeader(longLivedToken))
      .queryParam("type", "shortLived")
      .queryParam("deviceId", DEVICE_ID)
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("token")

    `given`
      .basePath("/jmap")
      .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER, s"Bearer $jwtToken")))
      .body(ECHO_REQUEST_OBJECT)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
  }

  @Test
  def getTokenShouldFailWhenMissingTypeParameter(): Unit = {
    val longLivedToken: String = createLongLivedToken()

    val response: String = `given`()
      .header(buildBearerTokenHeader(longLivedToken))
      .queryParam("deviceId", DEVICE_ID)
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 400,
           |    "detail": "Invalid request: 'type' must be shortLived"
           |}""".stripMargin)
  }

  @Test
  def getTokenShouldFailWhenMissingDeviceIdParameter(): Unit = {
    val longLivedToken: String = createLongLivedToken()

    val response: String = `given`()
      .header(buildBearerTokenHeader(longLivedToken))
      .queryParam("type", "shortLived")
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 400,
           |    "detail": "Invalid request: 'deviceId' must be not empty"
           |}""".stripMargin)
  }

  @Test
  def getTokenShouldFailWhenLongLivedTokenIsInvalid(): Unit = {
    val response: String = `given`()
      .header(   new Header(AUTHORIZATION_HEADER, s"Bearer bob_123"))
      .queryParam("type", "shortLived")
      .queryParam("deviceId", DEVICE_ID)
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(HttpStatus.SC_UNAUTHORIZED)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 401,
           |    "detail": "No valid authentication methods provided"
           |}""".stripMargin)
  }

  @Test
  def getTokenShouldFailWhenBadDeviceId(): Unit = {
    val longLivedToken: String = createLongLivedToken()

    val response: String = `given`()
      .header(buildBearerTokenHeader(longLivedToken))
      .queryParam("type", "shortLived")
      .queryParam("deviceId", "badDeviceId")
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(HttpStatus.SC_UNAUTHORIZED)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 401,
           |    "detail": "'deviceId' is not valid"
           |}""".stripMargin)
  }

  @Test
  def getTokenShouldFailWithRevokedLongLivedToken(): Unit = {
    val longLivedTokenRequest: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "K38": {
         |          "deviceId": "$DEVICE_ID"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val longLivedTokenResponse: String = `given`()
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .body(longLivedTokenRequest)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .body()
      .asString()

    val longLivedTokenId: String = (((Json.parse(longLivedTokenResponse) \\ "methodResponses" )
      .head \\ "created")
      .head \\ "id")
      .head.asInstanceOf[JsString].value

    val longLivedToken: String = (((Json.parse(longLivedTokenResponse) \\ "methodResponses" )
      .head \\ "created")
      .head \\ "token")
      .head.asInstanceOf[JsString].value

    val revokeRequest: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:long:lived:token"],
         |  "methodCalls": [
         |    ["LongLivedToken/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "destroy": ["$longLivedTokenId"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`
      .headers(getHeadersWith(BOB_BASIC_AUTH_HEADER))
      .body(revokeRequest)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)

    val response: String = `given`()
      .header(buildBearerTokenHeader(longLivedToken))
      .queryParam("type", "shortLived")
      .queryParam("deviceId", DEVICE_ID)
    .when()
      .basePath(BASE_PATH)
      .get()
    .`then`()
      .statusCode(SC_UNAUTHORIZED)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "type": "about:blank",
           |    "status": 401,
           |    "detail": "Invalid long lived token"
           |}""".stripMargin)
  }
}
