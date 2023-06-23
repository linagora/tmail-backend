package com.linagora.tmail.james.common

import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

trait TeamMailboxRevokeAccessMethodContract {
  private lazy val BOB_ACCOUNT_ID: String = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  private def mailboxId(server: GuiceJamesServer, path: MailboxPath) = {
    server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(path.getNamespace, path.getUser.asString(), path.getName)
      .serialize()
  }

  @Test
  def shouldFailWhenMissingTeamMailboxCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["mailboxA@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"error",
         |			{
         |				"type": "unknownMethod",
         |				"description": "Missing capability(ies): com:linagora:params:jmap:team:mailboxes"
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def givenBobBelongsToTeamMailboxThenRevokeAccessSucceedCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"TeamMailbox/revokeAccess",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"revoked": ["hiring@domain.tld"]
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[TeamMailboxProbe]).listMembers(teamMailbox).asJava)
      .isEmpty()
  }

  @Test
  def revokeAccessShouldEnsureNoLongerMailboxGetAccess(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
    val teamMailboxInboxId = mailboxId(server, teamMailbox.inboxPath)

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    val response = `given`
      .body(
        s"""{
           |	"using": [
           |		"urn:ietf:params:jmap:core",
           |		"urn:ietf:params:jmap:mail"
           |	],
           |	"methodCalls": [
           |		[
           |			"Mailbox/get",
           |			{
           |				"accountId": "$BOB_ACCOUNT_ID",
           |				"ids": ["$teamMailboxInboxId"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "Mailbox/get",
         |            {
         |                "accountId": "$BOB_ACCOUNT_ID",
         |                "notFound": ["$teamMailboxInboxId"],
         |                "state": "$${json-unit.ignore}",
         |                "list": []
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def leaveANonExistMailboxShouldFail(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["nonExistTeamMailbox@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "TeamMailbox/revokeAccess",
         |            {
         |                "accountId": "$BOB_ACCOUNT_ID",
         |                "notRevoked": {
         |                    "nonExistTeamMailbox@domain.tld": {
         |                        "type": "notFound",
         |                        "description": "#TeamMailbox:team-mailbox@domain.tld:nonExistTeamMailbox can not be found"
         |                    }
         |                }
         |            },
         |            "c0"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def revokeMailboxWithoutAtCharacterShouldFail(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [
         |        [
         |            "TeamMailbox/revokeAccess",
         |            {
         |                "accountId": "$BOB_ACCOUNT_ID",
         |                "notRevoked": {
         |                    "hiring": {
         |                        "type": "invalidArguments",
         |                        "description": "hiring is not a Team Mailbox: Missing '@' in mailbox FQDN"
         |                    }
         |                }
         |            },
         |            "c0"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def revokeMailboxWithInvalidCharacterShouldFail(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["/hiring@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"TeamMailbox/revokeAccess",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"notRevoked": {
         |					"/hiring@domain.tld": {
         |						"type": "invalidArguments",
         |						"description": "/hiring@domain.tld is not a Team Mailbox: Predicate failed: '/hiring@domain.tld' contains some invalid characters. Should be [#a-zA-Z0-9-_.@] and no longer than 320 chars."
         |					}
         |				}
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def revokeNonStringMailboxesShouldFail(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": [null]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      """{
         |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
         |    "methodResponses": [
         |        [
         |            "error",
         |            {
         |                "type": "invalidArguments",
         |                "description": "'/ids(0)' property is not valid: Team mailbox needs to be represented by a JsString"
         |            },
         |            "c0"
         |        ]
         |    ]
         |}""".stripMargin)
  }

  @Test
  def givenBobDoesNotHaveAccessToTeamMailboxThenRevokeAccessShouldSucceed(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"TeamMailbox/revokeAccess",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"revoked": ["hiring@domain.tld"]
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[TeamMailboxProbe]).listMembers(teamMailbox).asJava)
      .isEmpty()
  }

  @Test
  def revokeAccessShouldBeIdempotent(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val response1 = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response1).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"TeamMailbox/revokeAccess",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"revoked": ["hiring@domain.tld"]
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)

    val response2 = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring@domain.tld"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response2).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"TeamMailbox/revokeAccess",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"revoked": ["hiring@domain.tld"]
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def mixedCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$BOB_ACCOUNT_ID",
           |			"ids": ["hiring@domain.tld", "nonExistTeamMailbox@domain.tld", "invalid"]
           |		}, "c0"]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"TeamMailbox/revokeAccess",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"revoked": [
         |					"hiring@domain.tld"
         |				],
         |				"notRevoked": {
         |					"nonExistTeamMailbox@domain.tld": {
         |						"type": "notFound",
         |						"description": "#TeamMailbox:team-mailbox@domain.tld:nonExistTeamMailbox can not be found"
         |					},
         |					"invalid": {
         |						"type": "invalidArguments",
         |						"description": "invalid is not a Team Mailbox: Missing '@' in mailbox FQDN"
         |					}
         |				}
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def revokeTeamMailboxAccessShouldRejectDelegatee(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
         |	"methodCalls": [
         |		["TeamMailbox/revokeAccess", {
         |			"accountId": "$BOB_ACCOUNT_ID",
         |			"ids": ["hiring@domain.tld"]
         |		}, "c0"]
         |	]
         |}""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"error",
           |	{
           |		"type": "forbidden",
           |		"description": "Access to other accounts settings is forbidden"
           |	},
           |	"c0"
           |]""".stripMargin)

    assertThat(server.getProbe(classOf[TeamMailboxProbe]).listMembers(teamMailbox).asJava)
      .containsOnly(BOB)
  }
}
