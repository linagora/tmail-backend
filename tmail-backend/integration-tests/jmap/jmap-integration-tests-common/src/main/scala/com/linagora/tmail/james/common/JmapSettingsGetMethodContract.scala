package com.linagora.tmail.james.common

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsRepository, JmapSettingsStateFactory, JmapSettingsUpsertRequest, SettingsStateUpdate}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import javax.inject.Inject
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

class JmapSettingsProbeModule extends AbstractModule {
  override def configure(): Unit =
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[JmapSettingsProbe])
}

class JmapSettingsProbe @Inject()(jmapSettingsRepository: JmapSettingsRepository) extends GuiceProbe {
  def reset(username: Username, settings: JmapSettingsUpsertRequest): SettingsStateUpdate =
    SMono(jmapSettingsRepository.reset(username, settings)).block()

  def reset(username: Username, setting: Map[String, String]): SettingsStateUpdate = {
    val upsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(setting.map(kv => JmapSettingsKey.liftOrThrow(kv._1) -> JmapSettingsValue(kv._2)))
    reset(username, upsertRequest)
  }
}

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
