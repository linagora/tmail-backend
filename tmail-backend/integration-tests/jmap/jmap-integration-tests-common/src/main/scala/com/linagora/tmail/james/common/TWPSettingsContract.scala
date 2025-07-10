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

import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.JmapSettingsSetMethodContract.firebasePushClient
import com.linagora.tmail.james.common.probe.JmapSettingsProbe
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebasePushRequest}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{AfterEach, RepeatedTest, Tag, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, reset, when}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}

object TWPSettingsContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait TWPSettingsContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  def startJmapServer(twpSettingsEnabled: Map[String, Object]): GuiceJamesServer

  def stopJmapServer(): Unit

  def publishAmqpSettingsMessage(message: String): Unit

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

    reset(firebasePushClient)
    when(firebasePushClient.validateToken(any())).thenReturn(Mono.just(true))
    when(firebasePushClient.push(any(classOf[FirebasePushRequest]))).thenReturn(Mono.empty)

    server
  }

  @AfterEach
  def afterEach(): Unit = {
    stopJmapServer()
  }

  @Test
  def shouldAcceptModifyTWPSettingsWhenTWPReadOnlyPropertyProviderIsNotConfigured(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> ""))

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"update": {
           |					"singleton": {
           |						"settings": {
           |							"language": "fr",
           |							"twp.settings.version": "2"
           |						}
           |					}
           |				}
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
           |    "Settings/set",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "updated": {
           |            "singleton": {}
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def shouldNotUpdateTWPSettingsWhenTWPReadOnlyPropertyProviderIsNotConfigured(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> ""))

    publishAmqpSettingsMessage(
      s"""{
         |    "source": "twake-mail",
         |    "nickname": "${BOB.getLocalPart}",
         |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
         |    "timestamp": 176248374356283740,
         |    "payload": {
         |        "language": "fr",
         |        "email": "${BOB.asString()}"
         |    },
         |    "version": 1
         |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted { () =>
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
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |            {
           |                "id": "singleton",
           |                "settings": {}
           |            }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
    }
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldRejectModifyTWPSettingsWhenTWPReadOnlyPropertyProviderIsConfigured(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "TWPReadOnlyPropertyProvider"))

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"update": {
           |					"singleton": {
           |						"settings": {
           |							"language": "fr",
           |							"twp.settings.version": "2"
           |						}
           |					}
           |				}
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
           |    "Settings/set",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "oldState": "$${json-unit.ignore}",
           |        "newState": "$${json-unit.ignore}",
           |        "notUpdated": {
           |            "singleton": {
           |                "type": "invalidArguments",
           |                "description": "Cannot modify read-only settings: language, twp.settings.version"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def givenTWPReadOnlyPropertyProviderIsConfiguredAndUserHasNoSettingsYetThenTWPSettingsShouldBeCreatedWhenAmqpUpdate(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "TWPReadOnlyPropertyProvider"))

    publishAmqpSettingsMessage(
      s"""{
         |    "source": "twake-mail",
         |    "nickname": "${BOB.getLocalPart}",
         |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
         |    "timestamp": 176248374356283740,
         |    "payload": {
         |        "language": "fr",
         |        "email": "${BOB.asString()}"
         |    },
         |    "version": 1
         |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted { () =>
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
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |            {
           |                "id": "singleton",
           |                "settings": {
           |                    "language": "fr",
           |                    "twp.settings.version": "1"
           |                }
           |            }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
    }
  }

  @Test
  def givenTWPReadOnlyPropertyProviderIsConfiguredAndUserHasSettingsThenTWPSettingsShouldBeUpdatedWhenAmqpUpdate(): Unit = {
    val server = setUpJmapServer(Map("settings.readonly.properties.providers" -> "TWPReadOnlyPropertyProvider"))

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("language", "en"), ("twp.settings.version", "1")))

    publishAmqpSettingsMessage(
      s"""{
         |    "source": "twake-mail",
         |    "nickname": "${BOB.getLocalPart}",
         |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
         |    "timestamp": 176248374356283740,
         |    "payload": {
         |        "language": "fr",
         |        "email": "${BOB.asString()}"
         |    },
         |    "version": 2
         |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted { () =>
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
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |            {
           |                "id": "singleton",
           |                "settings": {
           |                    "language": "fr",
           |                    "twp.settings.version": "2"
           |                }
           |            }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
    }
  }

  @Test
  def shouldUpdateTheLatestVersionSettingsWhenMultipleAmqpUpdates(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "TWPReadOnlyPropertyProvider"))

    publishAmqpSettingsMessage(
      s"""{
         |    "source": "twake-mail",
         |    "nickname": "${BOB.getLocalPart}",
         |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
         |    "timestamp": 176248374356283740,
         |    "payload": {
         |        "language": "en",
         |        "email": "${BOB.asString()}"
         |    },
         |    "version": 1
         |}""".stripMargin)

    Thread.sleep(200)

    publishAmqpSettingsMessage(
      s"""{
         |    "source": "twake-mail",
         |    "nickname": "${BOB.getLocalPart}",
         |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
         |    "timestamp": 176248374356283740,
         |    "payload": {
         |        "language": "fr",
         |        "email": "${BOB.asString()}"
         |    },
         |    "version": 2
         |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted { () =>
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
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |            {
           |                "id": "singleton",
           |                "settings": {
           |                    "language": "fr",
           |                    "twp.settings.version": "2"
           |                }
           |            }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
    }
  }

  @RepeatedTest(3)
  def shouldUpdateTheLatestVersionSettingsWhenConcurrentAmqpUpdates(): Unit = {
    setUpJmapServer(Map("settings.readonly.properties.providers" -> "TWPReadOnlyPropertyProvider"))

    SFlux.zip(SMono.fromCallable(() => publishAmqpSettingsMessage(
      s"""{
         |    "source": "twake-mail",
         |    "nickname": "${BOB.getLocalPart}",
         |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
         |    "timestamp": 176248374356283740,
         |    "payload": {
         |        "language": "fr",
         |        "email": "${BOB.asString()}"
         |    },
         |    "version": 2
         |}""".stripMargin)),
      SMono.fromCallable(() => publishAmqpSettingsMessage(
        s"""{
           |    "source": "twake-mail",
           |    "nickname": "${BOB.getLocalPart}",
           |    "request_id": "7e52493c-e396-4162-b675-496c52b6ba1b",
           |    "timestamp": 176248374356283740,
           |    "payload": {
           |        "language": "en",
           |        "email": "${BOB.asString()}"
           |    },
           |    "version": 1
           |}""".stripMargin)))
      .collectSeq()
      .block()

    awaitAtMostTenSeconds.untilAsserted { () =>
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
           |        "state": "$${json-unit.ignore}",
           |        "list": [
           |            {
           |                "id": "singleton",
           |                "settings": {
           |                    "language": "fr",
           |                    "twp.settings.version": "2"
           |                }
           |            }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
    }
  }
}
