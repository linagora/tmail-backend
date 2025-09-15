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

import java.util.Optional

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.common.probe.JmapGuiceCustomProbe
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.filtering.Rule
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
trait LinagoraFilterGetMethodContract {

  def generateMailboxIdForUser(): String
  def generateUsername(): Username
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
  def filterGetWithUserHaveExistingRulesShouldShowThoseRules(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": ["singleton"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "contains",
         |              "value": "question"
         |            },
         |            "conditionGroup": {
         |              "conditionCombiner": "AND",
         |              "conditions": [
         |                {
         |                  "comparator": "contains",
         |                  "field": "subject",
         |                  "value": "question"
         |                }
         |              ]
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              },"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWithUserHaveNoRulesShouldShowEmptyRules(): Unit = {
    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": ["singleton"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "-1",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWithWrongAccountIdShouldFail(): Unit = {
    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af7",
                     |          "ids": ["singleton"]
                     |        },
                     |          "c1"]
                     |    ]
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
  def filterGetShouldReturnUnknownMethodWhenOmittingCapability(): Unit = {
    val request = s"""{
                     |  "using": [],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": ["singleton"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |  "methodResponses": [
         |    [
         |      "error",
         |        {
         |          "type": "unknownMethod",
         |          "description": "Missing capability(ies): com:linagora:params:jmap:filter"
         |        },
         |          "c1"
         |    ]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWhenAccountIdNullShouldFail(): Unit = {
    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": null,
                     |          "ids": ["singleton"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "error",
         |      {
         |        "type": "invalidArguments",
         |        "description": "'/accountId' property is not valid: error.expected.jsstring"
         |      },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWithIdsNullShouldReturnStoredFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": null
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "contains",
         |              "value": "question"
         |            },
         |            "conditionGroup": {
         |              "conditionCombiner": "AND",
         |              "conditions": [
         |                {
         |                  "comparator": "contains",
         |                  "field": "subject",
         |                  "value": "question"
         |                }
         |              ]
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              },"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def filterGetWithIdsContainSingletonShouldReturnStoredFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": ["singleton", "random"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "contains",
         |              "value": "question"
         |            },
         |            "conditionGroup": {
         |              "conditionCombiner": "AND",
         |              "conditions": [
         |                {
         |                  "comparator": "contains",
         |                  "field": "subject",
         |                  "value": "question"
         |                }
         |              ]
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              },"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": ["random"]
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWithIdsNotContainSingletonShouldReturnEmptyFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": ["random"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |
         |      ],
         |      "notFound": ["random"]
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWithEmptyIdsShouldReturnEmptyFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": []
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [],
         |      "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetShouldReturnRuleOfSomeConditionsWhenUserHaveRuleOfSomeConditions(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.OR, Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"),
            Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.CONTAINS, "user2")))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request =
      s"""{
         |  "using": ["com:linagora:params:jmap:filter" ],
         |  "methodCalls": [
         |    [
         |      "Filter/get",
         |        {
         |          "accountId": "$generateAccountIdAsString",
         |          "ids": ["singleton"]
         |        },
         |          "c1"]
         |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "conditionGroup": {
         |              "conditionCombiner": "OR",
         |              "conditions": [
         |                {
         |                  "comparator": "contains",
         |                  "field": "subject",
         |                  "value": "question"
         |                },
         |                {
         |                  "comparator": "contains",
         |                  "field": "from",
         |                  "value": "user2"
         |                }
         |              ]
         |            },
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "contains",
         |              "value": "question"
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              },"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetShouldReturnRuleWithActionForward(server: GuiceJamesServer): Unit = {
    val usernameString = "alice@james.org"
    val forwardedMailAddresses = ImmutableList.of(new MailAddress(usernameString))
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.AND,
            Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question")))
          .action(Rule.Action.builder().setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds())
            .setWithKeywords(ImmutableList.of)
            .setForward(Optional.of(Rule.Action.Forward.of(forwardedMailAddresses, true)))
            .build())
          .build)

    val request =
      s"""{
         |  "using": ["com:linagora:params:jmap:filter" ],
         |  "methodCalls": [
         |    [
         |      "Filter/get",
         |        {
         |          "accountId": "$generateAccountIdAsString",
         |          "ids": ["singleton"]
         |        },
         |          "c1"]
         |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "conditionGroup": {
         |              "conditionCombiner": "AND",
         |              "conditions": [
         |                {
         |                  "comparator": "contains",
         |                  "field": "subject",
         |                  "value": "question"
         |                }
         |              ]
         |            },
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "contains",
         |              "value": "question"
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":[]
         |              },
         |              "markAsSeen":false,
         |              "markAsImportant":false,
         |              "reject":false,
         |              "withKeywords":[],
         |              "forwardTo": {
         |                "addresses": ["$usernameString"],
         |                "keepACopy":true
         |              }
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetShouldReturnStartwithRule(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.START_WITH, "question"))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request = s"""{
                     |  "using": ["com:linagora:params:jmap:filter" ],
                     |  "methodCalls": [
                     |    [
                     |      "Filter/get",
                     |        {
                     |          "accountId": "$generateAccountIdAsString",
                     |          "ids": ["singleton"]
                     |        },
                     |          "c1"]
                     |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "start-with",
         |              "value": "question"
         |            },
         |            "conditionGroup": {
         |              "conditionCombiner": "AND",
         |              "conditions": [
         |                {
         |                  "comparator": "start-with",
         |                  "field": "subject",
         |                  "value": "question"
         |                }
         |              ]
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              },"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetShouldReturnCombinationOfStartWithAndOthersRules(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.ConditionGroup.of(Rule.ConditionCombiner.OR, Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"),
            Rule.Condition.of(Rule.Condition.Field.FROM, Rule.Condition.Comparator.START_WITH, "user")))
          .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(generateMailboxIdForUser())))
          .build)

    val request =
      s"""{
         |  "using": ["com:linagora:params:jmap:filter" ],
         |  "methodCalls": [
         |    [
         |      "Filter/get",
         |        {
         |          "accountId": "$generateAccountIdAsString",
         |          "ids": ["singleton"]
         |        },
         |          "c1"]
         |    ]
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
         |  "methodResponses": [[
         |    "Filter/get", {
         |      "accountId": "$generateAccountIdAsString",
         |      "state": "0",
         |      "list": [
         |      {
         |        "id": "singleton",
         |        "rules": [
         |          {
         |            "name": "My first rule",
         |            "conditionGroup": {
         |              "conditionCombiner": "OR",
         |              "conditions": [
         |                {
         |                  "comparator": "contains",
         |                  "field": "subject",
         |                  "value": "question"
         |                },
         |                {
         |                  "comparator": "start-with",
         |                  "field": "from",
         |                  "value": "user"
         |                }
         |              ]
         |            },
         |            "condition": {
         |              "field": "subject",
         |              "comparator": "contains",
         |              "value": "question"
         |            },
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              },"markAsSeen":false,"markAsImportant":false,"reject":false,"withKeywords":[]
         |            }
         |          }
         |        ]
         |      }
         |    ],
         |    "notFound": []
         |  }, "c1"]]
         |}""".stripMargin)
  }
}
