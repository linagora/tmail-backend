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
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.{AfterEach, Tag, Test}

trait JmapSettingsGetMethodContract {
  def startJmapServer(overrideJmapProperties: Map[String, Object]): GuiceJamesServer

  def stopJmapServer(): Unit

  private def setUpJmapServer(overrideJmapProperties: Map[String, Object]): GuiceJamesServer = {
    val server = startJmapServer(overrideJmapProperties)
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    server
  }

  @AfterEach
  def afterEach(): Unit = {
    stopJmapServer()
  }

  @Test
  def shouldReturnJmapSettingsCapabilityWithEmptyReadOnlyPropertiesInSessionRouteByDefault(): Unit = {
    setUpJmapServer(Map())

    `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:settings"))
      .body("capabilities.'com:linagora:params:jmap:settings'", hasKey("readOnlyProperties"))
      .body("capabilities.'com:linagora:params:jmap:settings'.readOnlyProperties", Matchers.empty())
  }

  @Test
  def shouldReturnLanguageReadOnlyPropertyInSessionRouteWhenConfigureFQDNFixedLanguageReadOnlyPropertyProvider(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "com.linagora.tmail.james.jmap.settings.FixedLanguageReadOnlyPropertyProvider"))

    `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:settings"))
      .body("capabilities.'com:linagora:params:jmap:settings'", hasKey("readOnlyProperties"))
      .body("capabilities.'com:linagora:params:jmap:settings'.readOnlyProperties", Matchers.contains("language"))
  }

  @Test
  def shouldReturnLanguageReadOnlyPropertyInSessionRouteWhenConfigureFixedLanguageReadOnlyPropertyProviderWithoutPackagePrefix(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "FixedLanguageReadOnlyPropertyProvider"))

    `given`()
    .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:settings"))
      .body("capabilities.'com:linagora:params:jmap:settings'", hasKey("readOnlyProperties"))
      .body("capabilities.'com:linagora:params:jmap:settings'.readOnlyProperties", Matchers.contains("language"))
  }

  @Test
  def missingSettingsCapabilityShouldFail(): Unit = {
    setUpJmapServer(Map())

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
  }


  @Test
  def getShouldReturnEmptySettingsByDefault(): Unit = {
    setUpJmapServer(Map())

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
  }

  @Test
  def shouldReturnReadOnlyLanguageSettingsWhenConfigureFixedLanguageReadOnlyPropertyProvider(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "FixedLanguageReadOnlyPropertyProvider"))

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
           |                "id": "singleton",
           |                "settings": {
           |                    "language": "en"
           |                }
           |            }],
           |        "notFound": [] },"c1"
           |]""".stripMargin))

  }

  @Test
  def mixReadOnlySettingsAndUserSettingsCase(): Unit = {
    val server = setUpJmapServer(Map("settings.readonly.properties.providers" -> "FixedLanguageReadOnlyPropertyProvider"))

    val settingsStateUpdate = server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("os", "linux")))

    // Settings/get should return the read-only language setting instead of the user setting.
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
           |                    "language": "en",
           |                    "os": "linux"
           |                }
           |            }],
           |        "notFound": [] },"c1"
           |]""".stripMargin))

  }

  @Test
  def readOnlySettingsShouldOverrideUserSettings(): Unit = {
    val server = setUpJmapServer(Map("settings.readonly.properties.providers" -> "FixedLanguageReadOnlyPropertyProvider"))

    // Assume user BOB had set FR language setting before read-only provider is configured.
    val settingsStateUpdate = server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("language", "fr")))

    // Settings/get should return the read-only language setting instead of the user setting.
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
           |                    "language": "en"
           |                }
           |            }],
           |        "notFound": [] },"c1"
           |]""".stripMargin))

  }

  @Test
  def fetchNullIdsShouldReturnSettings(): Unit = {
    val server = setUpJmapServer(Map())

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
  def shouldReturnNotFoundWhenIsNotSingletonId(): Unit = {
    setUpJmapServer(Map())

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
  }


  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def getShouldReturnValidResponseWhenSingletonId(): Unit = {
    setUpJmapServer(Map())

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
  }


  @Test
  def mixedFoundAndNotFoundCase(): Unit = {
    val server = setUpJmapServer(Map())

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
  def shouldFailWhenWrongAccountId(): Unit = {
    setUpJmapServer(Map())

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
  }

  @Test
  def shouldSupportDelegation(): Unit = {
    val server = setUpJmapServer(Map())

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
  def shouldFailWhenNotDelegated(): Unit = {
    val server = setUpJmapServer(Map())

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
  def missingCoreCapabilityShouldFail(): Unit = {
    setUpJmapServer(Map())

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

}
