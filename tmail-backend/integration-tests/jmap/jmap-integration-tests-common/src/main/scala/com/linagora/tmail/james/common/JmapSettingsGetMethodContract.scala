package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.probe.JmapSettingsProbe
import com.linagora.tmail.james.jmap.settings.JmapSettingsStateFactory
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.{BeforeEach, Test}

trait JmapSettingsGetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def shouldReturnJmapSettingsCapabilityInSessionRoute(): Unit =
    `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:settings"))

  @Test
  def missingSettingsCapabilityShouldFail(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"error",
           |			{
           |				"type": "unknownMethod",
           |				"description": "Missing capability(ies): com:linagora:params:jmap:settings"
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin))


  @Test
  def getShouldReturnEmptySettingsByDefault(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "Settings/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |        "list": [{
           |                   "id": "singleton",
           |                   "settings": {}
           |                }],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))

  @Test
  def fetchNullIdsShouldReturnSettings(server: GuiceJamesServer): Unit = {
    val settingsStateUpdate = server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1")))

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "Settings/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "${settingsStateUpdate.newState.serialize}",
           |        "list": [{
           |                "id": "singleton",
           |                "settings": {
           |                    "key1": "value1"
           |                }
           |            }],
           |        "notFound": [] },"c1"
           |]""".stripMargin))

  }

  @Test
  def shouldReturnNotFoundWhenIsNotSingletonId(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["notFound1"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "Settings/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "list": [],
           |        "notFound": [
           |            "notFound1"
           |        ]
           |    },
           |    "c1"
           |]""".stripMargin))


  @Test
  def getShouldReturnValidResponseWhenSingletonId(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "Settings/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "list": [{
           |                    "id": "singleton",
           |                    "settings": {}
           |                }],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))


  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val settingsStateUpdate = server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1")))

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton", "notFound2"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "Settings/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "state": "${settingsStateUpdate.newState.serialize}",
           |        "list": [{
           |                    "id": "singleton",
           |                    "settings": {"key1": "value1"}
           |                }],
           |        "notFound": ["notFound2"]
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def shouldFailWhenWrongAccountId(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "unknownAccountId",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["error", {
           |      "type": "accountNotFound"
           |    }, "c1"]
           |  ]
           |}""".stripMargin))

  @Test
  def shouldSupportDelegation(server: GuiceJamesServer): Unit = {
    val bobAccountId: String = ACCOUNT_ID
    val settingsStateUpdate = server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1")))

    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    `given`
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "Settings/get",
           |    {
           |        "accountId": "$bobAccountId",
           |        "state": "${settingsStateUpdate.newState.serialize}",
           |        "list": [{
           |                    "id": "singleton",
           |                    "settings": {"key1": "value1"}
           |                }],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def shouldFailWhenNotDelegated(server: GuiceJamesServer): Unit = {
    val bobAccountId: String = ACCOUNT_ID
    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1")))

    `given`
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |    "error",
           |    {
           |        "type": "accountNotFound"
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def missingCoreCapabilityShouldFail(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": null
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"error",
           |			{
           |				"type": "unknownMethod",
           |				"description": "Missing capability(ies): urn:ietf:params:jmap:core"
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin))

}
