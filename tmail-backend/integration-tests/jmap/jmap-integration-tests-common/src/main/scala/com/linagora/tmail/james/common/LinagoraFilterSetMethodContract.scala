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

import com.linagora.tmail.james.common.LinagoraFilterSetMethodContract.firebasePushClient
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebasePushRequest}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, reset, times, verify, when}
import reactor.core.publisher.Mono

object LinagoraFilterSetMethodContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait LinagoraFilterSetMethodContract {

  def generateMailboxIdForUser(): String
  def generateMailboxId2ForUser(): String
  def generateAccountIdAsString(): String

  @BeforeEach
  def setUp(server : GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build()

    reset(firebasePushClient)
    when(firebasePushClient.validateToken(any())).thenReturn(Mono.just(true))
    when(firebasePushClient.push(any(classOf[FirebasePushRequest]))).thenReturn(Mono.empty)
  }

  @Test
  def updateRulesWithEmptyIfInStateWhenNonStateIsDefinedShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "subject",
         |						"comparator": "contains",
         |						"value": "question"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": ["$generateMailboxIdForUser"]
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |							},"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateWithMultipleMailboxesShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "subject",
         |						"comparator": "contains",
         |						"value": "question"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": ["$generateMailboxIdForUser", "$generateMailboxId2ForUser"]
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .when(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser", "$generateMailboxId2ForUser"]
         |							},"markAsImportant":false,"markAsSeen":false,"reject":false,"withKeywords":[]
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Tag(CategoryTags.BASIC_FEATURE)
  @Test
  def updateRulesShouldSupportExtraFields(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						},
                     |            "markAsSeen": true,
                     |            "markAsImportant": true,
                     |            "reject": true,
                     |            "withKeywords": ["abc", "def"]
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"],
                     |		[
                     |			"Filter/get",
                     |			{
                     |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                     |				"ids": ["singleton"]
                     |			},
                     |			"c2"
                     |		]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |              },
         |              "markAsSeen": true,
         |              "markAsImportant": true,
         |              "reject": true,
         |              "withKeywords": ["abc", "def"]
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateRulesShouldSupportActionForward(): Unit = {
    val usernameString = "alice@james.org"
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "subject",
         |						"comparator": "contains",
         |						"value": "question"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": []
         |						},
         |						"forwardTo": {
         |							"addresses": ["$usernameString"],
         |							"keepACopy":true
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": []
         |							},
         |							"markAsSeen": false,
         |							"markAsImportant": false,
         |							"reject": false,
         |							"withKeywords": [],
         |							"forwardTo": {
         |								"addresses": ["$usernameString"],
         |								"keepACopy":true
         |							}
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateRulesShouldFailWhenActionForwardContainsMailAddressOfCurrentUser(): Unit = {
    val usernameString = BOB.asString()
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "subject",
         |						"comparator": "contains",
         |						"value": "question"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": []
         |						},
         |						"withKeywords": [],
         |						"forwardTo": {
         |							"addresses": ["$usernameString"],
         |							"keepACopy":true
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "The mail address that are forwarded to could not be this mail address"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldSupportActionMoveTo(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "subject",
         |						"comparator": "contains",
         |						"value": "question"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": []
         |						},
         |						"moveTo": {
         |							"mailboxName": "Inbox.subfolder"
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": []
         |							},
         |							"markAsSeen": false,
         |							"markAsImportant": false,
         |							"reject": false,
         |							"withKeywords": [],
         |							"moveTo": {
         |								"mailboxName": "Inbox.subfolder"
         |							}
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldSupportCustomHeaderField(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "header:X-custom",
         |						"comparator": "contains",
         |						"value": "question"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": ["$generateMailboxIdForUser"]
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "header:X-custom",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "header:X-custom",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |							},
         |							"markAsSeen": false,
         |							"markAsImportant": false,
         |							"reject": false,
         |							"withKeywords": []
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldSupportSentDateCriteria(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "sentDate",
         |						"comparator": "isOlderThan",
         |						"value": "30d"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": ["$generateMailboxIdForUser"]
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "sentDate",
         |									"comparator": "isOlderThan",
         |									"value": "30d"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "sentDate",
         |							"comparator": "isOlderThan",
         |							"value": "30d"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |							},
         |							"markAsSeen": false,
         |							"markAsImportant": false,
         |							"reject": false,
         |							"withKeywords": []
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldSupportCustomHeaderExistenceWithAnyComparator(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"condition": {
         |						"field": "header:X-custom",
         |						"comparator": "any",
         |						"value": "disregard me"
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": ["$generateMailboxIdForUser"]
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "header:X-custom",
         |									"comparator": "any",
         |									"value": "disregard me"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "header:X-custom",
         |							"comparator": "any",
         |							"value": "disregard me"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |							},
         |							"markAsSeen": false,
         |							"markAsImportant": false,
         |							"reject": false,
         |							"withKeywords": []
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def filterSetShouldReturnUnknownMethodWhenOmittingCapability(): Unit = {
    val request = s"""{
                     |	"using": [],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"error",
         |			{
         |				"type": "unknownMethod",
         |				"description": "Missing capability(ies): com:linagora:params:jmap:filter"
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def filterSetWithWrongAccountIdShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af7",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    [
         |      "error",
         |        {
         |          "type": "accountNotFound"
         |        },
         |          "c1"
         |    ]]
         |}""".stripMargin)
  }

  @Test
  def updateSomethingElseThanSingletonShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton2": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notUpdated": {
         |					"singleton2": {
         |						"type": "invalidArguments",
         |						"description": "id singleton2 must be singleton"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateShouldFailWhenMultiplePatchObjects(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}],
                     |				"singleton2": [{
                     |					"id": "2",
                     |					"name": "My second rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c2"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				},
         |				"notUpdated": {
         |					"singleton2": {
         |						"type": "invalidArguments",
         |						"description": "id singleton2 must be singleton"
         |					}
         |				}
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def createShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"create": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin


    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notCreated": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "'create' is not supported on singleton objects"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def destroyShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"destroy": ["singleton"]
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin


    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notDestroyed": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "'destroy' is not supported on singleton objects"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateWithInvalidFieldShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "wrongField",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "'wrongField' is not a valid field name"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateWithInvalidComparatorShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "wrongComparator",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "'wrongComparator' is not a valid comparator name"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateWithEmptyValueShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": ""
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Filter/set",
         |            {
         |                "accountId": "$generateAccountIdAsString",
         |                "oldState": "-1",
         |                "newState": "-1",
         |                "notUpdated": {
         |                    "singleton": {
         |                        "type": "invalidArguments",
         |                        "description": "value should not be empty"
         |                    }
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def updateDuplicatedRuleIdsShouldFail(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"update": {
                     |				"singleton": [
                     |          {
                     |					  "id": "1",
                     |					  "name": "My first rule",
                     |					  "condition": {
                     |						  "field": "subject",
                     |						  "comparator": "contains",
                     |						  "value": "question"
                     |					  },
                     |					  "action": {
                     |						  "appendIn": {
                     |							  "mailboxIds": ["$generateMailboxIdForUser"]
                     |						  }
                     |					  }
                     |				},
                     |        {
                     |					"id": "1",
                     |					"name": "My second rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "-1",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "There are some duplicated rules"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def givenARuleWithStateIsZeroThenUpdateRulesWithIfInStateIsZeroShouldSucceed(): Unit = {
    val request1 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "1",
                      |					"name": "My first rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)

    val request2 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"ifInState": "0",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "2",
                      |					"name": "My second rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"],
                      |		[
                      |			"Filter/get",
                      |			{
                      |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                      |				"ids": ["singleton"]
                      |			},
                      |			"c2"
                      |		]
                      |	]
                      |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "0",
         |				"newState": "1",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "1",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My second rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |							},"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateRulesWithEmptyIfInStateWhenAStateIsDefinedShouldSucceed(): Unit = {
    val request1 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "1",
                      |					"name": "My first rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)

    val request2 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "2",
                      |					"name": "My second rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"oldState": "0",
         |				"newState": "1",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateWithIfInStateIsInitialWhenNonStateIsDefinedShouldSucceed(): Unit = {
    val request = s"""{
                     |	"using": ["com:linagora:params:jmap:filter"],
                     |	"methodCalls": [
                     |		["Filter/set", {
                     |			"accountId": "$generateAccountIdAsString",
                     |			"ifInState": "-1",
                     |			"update": {
                     |				"singleton": [{
                     |					"id": "1",
                     |					"name": "My first rule",
                     |					"condition": {
                     |						"field": "subject",
                     |						"comparator": "contains",
                     |						"value": "question"
                     |					},
                     |					"action": {
                     |						"appendIn": {
                     |							"mailboxIds": ["$generateMailboxIdForUser"]
                     |						}
                     |					}
                     |				}]
                     |			}
                     |		}, "c1"],
                     |		[
                     |			"Filter/get",
                     |			{
                     |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                     |				"ids": ["singleton"]
                     |			},
                     |			"c2"
                     |		]
                     |	]
                     |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "$generateAccountIdAsString",
         |				"oldState": "-1",
         |				"newState": "0",
         |				"updated": {
         |					"singleton": {
         |
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		[
         |			"Filter/get", {
         |				"accountId": "$generateAccountIdAsString",
         |				"state": "0",
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"conditionGroup": {
         |							"conditionCombiner": "AND",
         |							"conditions": [
         |								{
         |									"field": "subject",
         |									"comparator": "contains",
         |									"value": "question"
         |								}
         |							]
         |						},
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
         |							},"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |						}
         |					}]
         |				}],
         |				"notFound": []
         |			}, "c2"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateWithIfInStateIsInitialWhenAStateIsDefinedShouldFail(): Unit = {
    val request1 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "1",
                      |					"name": "My first rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)

    val request2 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"ifInState": "-1",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "2",
                      |					"name": "My second rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"oldState": "0",
         |				"newState": "0",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "stateMismatch",
         |						"description": "Provided state must be as same as the current state"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateRulesWithWrongCurrentStateShouldFail(): Unit = {
    val request1 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "1",
                      |					"name": "My first rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request1)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)

    val request2 = s"""{
                      |	"using": ["com:linagora:params:jmap:filter"],
                      |	"methodCalls": [
                      |		["Filter/set", {
                      |			"accountId": "$generateAccountIdAsString",
                      |			"ifInState": "1",
                      |			"update": {
                      |				"singleton": [{
                      |					"id": "2",
                      |					"name": "My second rule",
                      |					"condition": {
                      |						"field": "subject",
                      |						"comparator": "contains",
                      |						"value": "question"
                      |					},
                      |					"action": {
                      |						"appendIn": {
                      |							"mailboxIds": ["$generateMailboxIdForUser"]
                      |						}
                      |					}
                      |				}]
                      |			}
                      |		}, "c1"]
                      |	]
                      |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request2)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |	"methodResponses": [
         |		[
         |			"Filter/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"oldState": "0",
         |				"newState": "0",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "stateMismatch",
         |						"description": "Provided state must be as same as the current state"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def updateRuleWithSomeConditionsShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["com:linagora:params:jmap:filter"],
         |	"methodCalls": [
         |		["Filter/set", {
         |			"accountId": "$generateAccountIdAsString",
         |			"update": {
         |				"singleton": [{
         |					"id": "1",
         |					"name": "My first rule",
         |					"conditionGroup": {
         |						"conditionCombiner": "OR",
         |						"conditions": [
         |							{
         |								"field": "subject",
         |								"comparator": "contains",
         |								"value": "question"
         |							},
         |							{
         |								"field": "from",
         |								"comparator": "contains",
         |								"value": "user2"
         |							}
         |						]
         |					},
         |					"action": {
         |						"appendIn": {
         |							"mailboxIds": ["$generateMailboxIdForUser"]
         |						}
         |					}
         |				}]
         |			}
         |		}, "c1"],
         |		[
         |			"Filter/get",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["singleton"]
         |			},
         |			"c2"
         |		]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Filter/set",
           |			{
           |				"accountId": "$generateAccountIdAsString",
           |				"oldState": "-1",
           |				"newState": "0",
           |				"updated": {
           |					"singleton": {
           |
           |					}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Filter/get", {
           |				"accountId": "$generateAccountIdAsString",
           |				"state": "0",
           |				"list": [{
           |					"id": "singleton",
           |					"rules": [{
           |						"name": "My first rule",
           |						"conditionGroup": {
           |							"conditionCombiner": "OR",
           |							"conditions": [
           |								{
           |									"field": "subject",
           |									"comparator": "contains",
           |									"value": "question"
           |								},
           |								{
           |									"field": "from",
           |									"comparator": "contains",
           |									"value": "user2"
           |								}
           |							]
           |						},
           |						"condition": {
           |							"field": "subject",
           |							"comparator": "contains",
           |							"value": "question"
           |						},
           |						"action": {
           |							"appendIn": {
           |								"mailboxIds": ["$generateMailboxIdForUser"]
           |							},"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
           |						}
           |					}]
           |				}],
           |				"notFound": []
           |			}, "c2"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def shouldNotPushToFCMWhenFailureFilterSet(): Unit = {
    registerFilterChangesViaFCM()

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |	"using": ["com:linagora:params:jmap:filter"],
           |	"methodCalls": [
           |		["Filter/set", {
           |			"accountId": "$generateAccountIdAsString",
           |			"create": {
           |				"singleton": [{
           |					"id": "1",
           |					"name": "My first rule",
           |					"condition": {
           |						"field": "subject",
           |						"comparator": "contains",
           |						"value": "question"
           |					},
           |					"action": {
           |						"appendIn": {
           |							"mailboxIds": ["$generateMailboxIdForUser"]
           |						}
           |					}
           |				}]
           |			}
           |		}, "c1"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |	"Filter/set",
           |	{
           |		"accountId": "$generateAccountIdAsString",
           |		"oldState": "-1",
           |		"newState": "-1",
           |		"notCreated": {
           |			"singleton": {
           |				"type": "invalidArguments",
           |				"description": "'create' is not supported on singleton objects"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin))

    verify(firebasePushClient, times(0)).push(any())
  }

  @Test
  def shouldPushToFCMWhenSuccessFilterSet(): Unit = {
    registerFilterChangesViaFCM()

    val newState: String = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |	"using": ["com:linagora:params:jmap:filter"],
           |	"methodCalls": [
           |		["Filter/set", {
           |			"accountId": "$generateAccountIdAsString",
           |			"update": {
           |				"singleton": [{
           |					"id": "1",
           |					"name": "My first rule",
           |					"condition": {
           |						"field": "subject",
           |						"comparator": "contains",
           |						"value": "question"
           |					},
           |					"action": {
           |						"appendIn": {
           |							"mailboxIds": ["$generateMailboxIdForUser"]
           |						}
           |					}
           |				}]
           |			}
           |		}, "c1"]
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

    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient, times(1)).push(argumentCaptor.capture())

      val stateChangesCapture: java.util.Map[String, String] = argumentCaptor.getValue.stateChangesMap()
      SoftAssertions.assertSoftly(softLy => {
        softLy.assertThat(stateChangesCapture).containsOnlyKeys(s"$generateAccountIdAsString:Filter")
        softLy.assertThat(stateChangesCapture).containsValue(newState)
      })
    })
  }

  private def registerFilterChangesViaFCM(): Unit =
    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
           |                  "types": ["Filter"]
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

}
