package com.linagora.openpaas.james.common

import com.linagora.openpaas.james.common.probe.JmapGuiceCustomProbe
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.filtering.{Rule, Version}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.OptionConverters._

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
        Option(Version.INITIAL).toJava,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
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
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
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
         |        "description": "{\\"errors\\":[{\\"path\\":\\"obj.accountId\\",\\"messages\\":[\\"error.expected.jsstring\\"]}]}"
         |      },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def filterGetWithIdsNullShouldReturnStoredFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Option(Version.INITIAL).toJava,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
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
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
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
  def filterGetWithIdsContainSingletonShouldReturnStoredFilter(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(generateUsername(),
        Option(Version.INITIAL).toJava,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
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
         |            "action": {
         |              "appendIn": {
         |                "mailboxIds":["$generateMailboxIdForUser"]
         |              }
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
        Option(Version.INITIAL).toJava,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
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
        Option(Version.INITIAL).toJava,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .condition(Rule.Condition.of(Rule.Condition.Field.SUBJECT, Rule.Condition.Comparator.CONTAINS, "question"))
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

}
