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

import com.linagora.tmail.james.common.JmapSettingsSetMethodContract.firebasePushClient
import com.linagora.tmail.james.common.probe.JmapSettingsProbe
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebasePushRequest}
import com.linagora.tmail.james.jmap.settings.JmapSettingsStateFactory
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.path.json.JsonPath
import net.javacrumbs.jsonunit.JsonMatchers
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.{AfterEach, Tag, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, reset, times, verify, when}
import reactor.core.publisher.Mono

object JmapSettingsSetMethodContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait JmapSettingsSetMethodContract {
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
  def missingSettingsCapabilityShouldFail(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"update": {
           |					"singleton": {
           |						"settings": {
           |							"tdrive.attachment.import.enabled": "true"
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
  def settingsSetShouldFailWhenWrongAccountId(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "wrongAccountId",
           |				"update": {
           |					"singleton": {
           |						"settings": {
           |							"tdrive.attachment.import.enabled": "true",
           |							"firebase.enabled": "true"
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
  @Tag(CategoryTags.BASIC_FEATURE)
  def fullResetShouldInsertNewSettings(): Unit = {
    setUpJmapServer(Map())

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
           |							"key1": "value1",
           |							"key2": "value2"
           |						}
           |					}
           |				}
           |			}, "c1"
           |		],
           |		[
           |			"Settings/get", {
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton"]
           |			}, "c2"
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
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"oldState": "$${json-unit.ignore}",
           |				"newState": "$${json-unit.ignore}",
           |				"updated": {
           |					"singleton": {}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "$${json-unit.ignore}",
           |				"list": [{
           |					"id": "singleton",
           |					"settings": {
           |						"key1": "value1",
           |						"key2": "value2"
           |					}
           |				}],
           |				"notFound": []
           |			},
           |			"c2"
           |		]
           |	]
           |}""".stripMargin))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def fullResetShouldPerformFullUpdateAndOverrideExistingSettings(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("toBeOverrideKey", "toBeOverrideValue")))

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
           |							"key1": "value1",
           |							"key2": "value2"
           |						}
           |					}
           |				}
           |			}, "c1"
           |		],
           |		[
           |			"Settings/get", {
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton"]
           |			}, "c2"
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
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"oldState": "$${json-unit.ignore}",
           |				"newState": "$${json-unit.ignore}",
           |				"updated": {
           |					"singleton": {}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Settings/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "$${json-unit.ignore}",
           |				"list": [{
           |					"id": "singleton",
           |					"settings": {
           |						"key1": "value1",
           |						"key2": "value2"
           |					}
           |				}],
           |				"notFound": []
           |			},
           |			"c2"
           |		]
           |	]
           |}""".stripMargin))
  }

  @Test
  def settingsSetShouldReturnCorrectStatesAfterSuccessUpdate(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("toBeOverrideKey", "toBeOverrideValue")))

    val beforeSettingsSetState: UuidState = server.getProbe(classOf[JmapSettingsProbe])
      .getLatestState(BOB)

    val response: JsonPath = `given`
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
           |							"key1": "value1",
           |							"key2": "value2"
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
      .extract()
      .jsonPath()

    val afterSettingsSetState: UuidState = server.getProbe(classOf[JmapSettingsProbe])
      .getLatestState(BOB)

    assertThat(response.getString("methodResponses[0][1].oldState")).isEqualTo(beforeSettingsSetState.serialize)
    assertThat(response.getString("methodResponses[0][1].newState")).isEqualTo(afterSettingsSetState.serialize)
  }

  @Test
  def settingsSetShouldReturnCorrectStatesAfterFailureUpdate(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("toBeOverrideKey", "toBeOverrideValue")))

    val beforeSettingsSetState: UuidState = server.getProbe(classOf[JmapSettingsProbe])
      .getLatestState(BOB)

    val response: JsonPath = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"update": {
           |					"singleton1": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
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
      .extract()
      .jsonPath()

    val afterSettingsSetState: UuidState = server.getProbe(classOf[JmapSettingsProbe])
      .getLatestState(BOB)

    assertThat(response.getString("methodResponses[0][1].oldState")).isEqualTo(beforeSettingsSetState.serialize)
    assertThat(response.getString("methodResponses[0][1].newState")).isEqualTo(afterSettingsSetState.serialize)
  }

  @Test
  def settingsSetShouldReturnInitialStatesByDefault(): Unit = {
    setUpJmapServer(Map())

    val failureResponse: JsonPath = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"update": {
           |					"invalidSingleton": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
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
      .extract()
      .jsonPath()

    assertThat(failureResponse.getString("methodResponses[0][1].oldState")).isEqualTo(JmapSettingsStateFactory.INITIAL.serialize)
    assertThat(failureResponse.getString("methodResponses[0][1].newState")).isEqualTo(JmapSettingsStateFactory.INITIAL.serialize)
  }

  @Test
  def settingsSetWithWrongSingletonIdShouldFail(): Unit = {
    setUpJmapServer(Map())

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
           |					"singleton1": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
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
           |	"Settings/set",
           |	{
           |		"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |		"oldState": "$${json-unit.ignore}",
           |		"newState": "$${json-unit.ignore}",
           |		"notUpdated": {
           |			"singleton1": {
           |				"type": "invalidArguments",
           |				"description": "id singleton1 must be singleton"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin))
  }

  @Test
  def settingsSetWithInvalidSettingKeyShouldFail(): Unit = {
    setUpJmapServer(Map())

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
           |							"invalid/setting/key": "value1"
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
           |                "description": "'/invalid/setting/key' property is not valid: Predicate failed: 'invalid/setting/key' contains some invalid characters. Should be [#a-zA-Z0-9-_#.] and no longer than 255 chars."
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def shouldFailWhenCreate(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"create": {
           |					"singleton": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
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
           |	"Settings/set",
           |	{
           |		"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |		"oldState": "$${json-unit.ignore}",
           |		"newState": "$${json-unit.ignore}",
           |		"notCreated": {
           |			"singleton": {
           |				"type": "invalidArguments",
           |				"description": "'create' is not supported on singleton objects"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin))
  }

  @Test
  def shouldFailWhenDestroy(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"destroy": ["singleton"]
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
           |	"Settings/set",
           |	{
           |		"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |		"oldState": "$${json-unit.ignore}",
           |		"newState": "$${json-unit.ignore}",
           |		"notDestroyed": {
           |			"singleton": {
           |				"type": "invalidArguments",
           |				"description": "'destroy' is not supported on singleton objects"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin))
  }

  @Test
  def mixedCaseCreateAndUpdateAndDestroy(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"create": {
           |					"singleton": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
           |						}
           |					}
           |				},
           |				"update": {
           |					"singleton": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
           |						}
           |					}
           |				},
           |				"destroy": ["singleton"]
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
           | 	"Settings/set",
           | 	{
           | 		"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           | 		"oldState": "$${json-unit.ignore}",
           | 		"newState": "$${json-unit.ignore}",
           | 		"updated": {
           | 			"singleton": {}
           | 		},
           | 		"notCreated": {
           | 			"singleton": {
           | 				"type": "invalidArguments",
           | 				"description": "'create' is not supported on singleton objects"
           | 			}
           | 		},
           | 		"notDestroyed": {
           | 			"singleton": {
           | 				"type": "invalidArguments",
           | 				"description": "'destroy' is not supported on singleton objects"
           | 			}
           | 		}
           | 	},
           | 	"c1"
           | ]""".stripMargin))
  }

  @Test
  def shouldForbiddenDelegation(): Unit = {
    val server = setUpJmapServer(Map())

    val bobAccountId: String = ACCOUNT_ID

    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    `given`
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |	"methodCalls": [
           |		[
           |			"Settings/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"update": {
           |					"singleton": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
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
           |	"error",
           |	{
           |		"type": "forbidden",
           |		"description": "Access to other accounts settings is forbidden"
           |	},
           |	"c1"
           |]""".stripMargin))
  }

  @Test
  def updatePartialWithRemovePatchShouldWork(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1"), ("key2", "value2")))

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
           |						"settings/key1": null
           |					}
           |				}
           |			}, "c1"
           |		],
           |		[
           |			"Settings/get", {
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton"]
           |			}, "c2"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[1][1].list[0]", jsonEquals(
        s"""{
           |    "id": "singleton",
           |    "settings": {
           |        "key2": "value2"
           |    }
           |}""".stripMargin))
  }

  @Test
  def updatePartialWithUpsertPatchShouldWork(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1")))

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
           |						"settings/key1": "value1New",
           |						"settings/key3": "value3"
           |					}
           |				}
           |			}, "c1"
           |		],
           |		[
           |			"Settings/get", {
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton"]
           |			}, "c2"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[1][1].list[0]", jsonEquals(
        s"""{
           |    "id": "singleton",
           |    "settings": {
           |        "key1": "value1New",
           |        "key3": "value3"
           |    }
           |}""".stripMargin))
  }

  @Test
  def updatePartialWithUpsertAndRemovePatchShouldWork(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("key1", "value1")))

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
           |						"settings/key1": null,
           |						"settings/key3": "value3"
           |					}
           |				}
           |			}, "c1"
           |		],
           |		[
           |			"Settings/get", {
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"ids": ["singleton"]
           |			}, "c2"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[1][1].list[0]", jsonEquals(
        s"""{
           |    "id": "singleton",
           |    "settings": {
           |        "key3": "value3"
           |    }
           |}""".stripMargin))
  }

  @Test
  def shouldFailWhenTryToUpdateBothPartialAndFullReset(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |  "methodCalls": [
           |    [
           |      "Settings/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |          "singleton": {
           |            "settings": {
           |              "key1": "value1New"
           |            },
           |            "settings/key1": null,
           |            "settings/key3": "value3"
           |          }
           |        }
           |      },
           |      "c1"
           |    ]
           |  ]
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
           |        "oldState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "newState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "notUpdated": {
           |            "singleton": {
           |                "type": "invalidArguments",
           |                "description": "Cannot perform both a reset and a partial update simultaneously"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def updatePartialShouldUpdateNewState(): Unit = {
    val server = setUpJmapServer(Map())

    val response =`given`
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
           |						"settings/key1": "value1"
           |					}
           |				}
           |			}, "c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()

    val afterSettingsSetState: UuidState = server.getProbe(classOf[JmapSettingsProbe])
      .getLatestState(BOB)

    assertThat(response.getString("methodResponses[0][1].oldState")).isEqualTo(JmapSettingsStateFactory.INITIAL.serialize)
    assertThat(response.getString("methodResponses[0][1].oldState")).isNotEqualTo("methodResponses[0][1].newState")
    assertThat(response.getString("methodResponses[0][1].newState")).isEqualTo(afterSettingsSetState.serialize)
  }

  @Test
  def updateShouldNoopWhenEmptyPatchObject(): Unit = {
    setUpJmapServer(Map())

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
           |					"singleton": {}
           |				}
           |			}, "c1"
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
           |        "oldState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "newState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "updated": {
           |            "singleton": {}
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @ParameterizedTest
  @ValueSource(strings = Array(
    "settingsinvalidKey",
    "settings/abc/xyz",
    "settings/abc@"
  ))
  def updatePartialShouldFailWhenInvalidKey(settingsKey: String): Unit = {
    setUpJmapServer(Map())

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
           |            "$settingsKey": "value1"
           |         }
           |				}
           |			}, "c1"
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
           |        "oldState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "newState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "notUpdated": {
           |            "singleton": {
           |                "type": "invalidArguments",
           |                "description": "$${json-unit.ignore}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def updatePartialShouldFailWhenInvalidSettingValue(): Unit = {
    setUpJmapServer(Map())

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:settings"],
           |  "methodCalls": [
           |    [
           |      "Settings/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |          "singleton": {
           |            "settings/key1": false
           |          }
           |        }
           |      },
           |      "c1"
           |    ]
           |  ]
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
           |        "oldState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "newState": "${JmapSettingsStateFactory.INITIAL.serialize}",
           |        "notUpdated": {
           |            "singleton": {
           |                "type": "invalidArguments",
           |                "description": "settings/key1 is not a valid partial update request"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def shouldPushToFCMWhenSettingsChanged(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("toBeOverrideKey", "toBeOverrideValue")))

    registerSettingsChangesViaFCM()
    val latestState: String = fullResetSettings()

    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient, times(1)).push(argumentCaptor.capture())

      val stateChangesCapture: java.util.Map[String, String] = argumentCaptor.getValue.stateChangesMap()
      assertSoftly(softLy => {
        softLy.assertThat(stateChangesCapture).containsOnlyKeys(s"$ACCOUNT_ID:Settings")
        softLy.assertThat(stateChangesCapture).containsValue(latestState)
      })
    })
  }

  @Test
  def shouldNotPushToFCMWhenFailureSettingsSet(): Unit = {
    val server = setUpJmapServer(Map())

    server.getProbe(classOf[JmapSettingsProbe])
      .reset(BOB, Map(("toBeOverrideKey", "toBeOverrideValue")))

    registerSettingsChangesViaFCM()
    failureFullResetSettings()

    verify(firebasePushClient, times(0)).push(any())
  }

  private def registerSettingsChangesViaFCM(): Unit =
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Settings"]
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].created", JsonMatchers.jsonNodePresent("4f29"))

  private def fullResetSettings(): String =
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
           |							"key1": "value1",
           |							"key2": "value2"
           |						}
           |					}
           |				}
           |			}, "c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .getString("methodResponses[0][1].newState")

  private def failureFullResetSettings(): Unit =
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
           |					"singleton1": {
           |						"settings": {
           |							"key1": "value1",
           |							"key2": "value2"
           |						}
           |					}
           |				}
           |			}, "c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
}
