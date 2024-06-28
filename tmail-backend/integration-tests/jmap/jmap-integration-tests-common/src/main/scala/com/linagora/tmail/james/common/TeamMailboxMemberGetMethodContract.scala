package com.linagora.tmail.james.common

import com.google.common.collect.ImmutableList
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait TeamMailboxMemberGetMethodContract {
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
  def missingTeamMailboxesCapabilityShouldFail(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
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
           |				"description": "Missing capability(ies): com:linagora:params:jmap:team:mailboxes"
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin))


  @Test
  def shouldFailWhenWrongAccountId(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
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
  def getShouldReturnEmptyListByDefault(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
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
           |    "TeamMailboxMember/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "list": [],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))

  @Test
  def fetchNullIdsShouldReturnMembersOfAllMailboxes(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(DOMAIN, TeamMailboxName("firing"))
    val teamMailbox3 = TeamMailbox(DOMAIN, TeamMailboxName("external"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
      .addManager(teamMailbox, ANDRE)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox2)
      .addMember(teamMailbox2, BOB)
      .addManager(teamMailbox2, ANDRE)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox3)
      .addMember(teamMailbox3, ANDRE)

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
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
           |    "TeamMailboxMember/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "list": [
           |                  {
           |                      "id": "${teamMailbox.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@domain.tld": {"role":"member"},
           |                          "andre@domain.tld": {"role":"manager"}
           |                      }
           |                  },
           |                  {
           |                      "id": "${teamMailbox2.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@domain.tld": {"role":"member"},
           |                          "andre@domain.tld": {"role":"manager"}
           |                      }
           |                  }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def fetchIdsShouldReturnMembersOfSpecificMailboxes(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(DOMAIN, TeamMailboxName("firing"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox2)
      .addMember(teamMailbox2, BOB)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "ids": [ "${teamMailbox.mailboxName.asString()}" ]
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
           |    "TeamMailboxMember/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "list": [
           |                  {
           |                      "id": "${teamMailbox.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@domain.tld": {"role":"member"}
           |                      }
           |                  }
           |        ],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def getShouldReturnNotFoundWhenUserIsNotMemberOfTheTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "ids": [
           |                  "${teamMailbox.mailboxName.asString()}"
           |        ]
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
           |    "TeamMailboxMember/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "list": [],
           |        "notFound": [ "${teamMailbox.mailboxName.asString()}" ]
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(DOMAIN, TeamMailboxName("firing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox2)
      .addMember(teamMailbox2, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/get",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "ids": [
           |                  "${teamMailbox.mailboxName.asString()}",
           |                  "${teamMailbox2.mailboxName.asString()}",
           |                  "notFound"
           |        ]
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
           |    "TeamMailboxMember/get",
           |    {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "list": [
           |                  {
           |                      "id": "${teamMailbox.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@domain.tld": {"role":"member"}
           |                      }
           |                  }
           |        ],
           |        "notFound": [ "${teamMailbox2.mailboxName.asString()}", "notFound" ]
           |    },
           |    "c1"
           |]""".stripMargin))
  }
}
