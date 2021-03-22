package com.linagora.openpaas.james.common

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.State.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait LinagoraFilterSetMethodContract {

  def generateMailboxIdForUser(): String
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
  }

  @Test
  def updateShouldSucceed(): Unit = {
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
         |				"newState": "${INSTANCE.value}",
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
         |				"list": [{
         |					"id": "singleton",
         |					"rules": [{
         |						"name": "My first rule",
         |						"condition": {
         |							"field": "subject",
         |							"comparator": "contains",
         |							"value": "question"
         |						},
         |						"action": {
         |							"appendIn": {
         |								"mailboxIds": ["$generateMailboxIdForUser"]
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
         |				"newState": "${INSTANCE.value}",
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
         |				"newState": "${INSTANCE.value}",
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
         |				"newState": "${INSTANCE.value}",
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
         |				"newState": "${INSTANCE.value}",
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
         |				"newState": "${INSTANCE.value}",
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
         |				"newState": "${INSTANCE.value}",
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
         |                "newState": "${INSTANCE.value}",
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
         |				"newState": "${INSTANCE.value}",
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
  def updateWithMultipleMailboxesShouldFail(): Unit = {
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
                     |							"mailboxIds": ["$generateMailboxIdForUser", "$generateMailboxIdForUser"]
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
         |				"newState": "${INSTANCE.value}",
         |				"notUpdated": {
         |					"singleton": {
         |						"type": "invalidArguments",
         |						"description": "There are some rules targeting several mailboxes"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

}
