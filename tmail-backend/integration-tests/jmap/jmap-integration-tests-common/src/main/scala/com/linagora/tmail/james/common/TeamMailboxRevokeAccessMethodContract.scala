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

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.model.MailboxPath
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

import scala.jdk.CollectionConverters._

object TeamMailboxRevokeAccessMethodContract {
  case class TestContext(domain: Domain) {
    val bobUsername: Username = Username.fromLocalPartWithDomain("bob", domain)
    val andreUsername: Username = Username.fromLocalPartWithDomain("andre", domain)
    val bobAccountId: String = Hashing.sha256().hashString(bobUsername.asString(), StandardCharsets.UTF_8).toString
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait TeamMailboxRevokeAccessMethodContract {
  def domain: Domain = TeamMailboxRevokeAccessMethodContract.currentContext.get().domain

  def bobUsername: Username = TeamMailboxRevokeAccessMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = TeamMailboxRevokeAccessMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = TeamMailboxRevokeAccessMethodContract.currentContext.get().andreUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    TeamMailboxRevokeAccessMethodContract.currentContext.set(TeamMailboxRevokeAccessMethodContract.TestContext(Domain.of(s"domain$uniqueSuffix.tld")))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(domain.asString())
      .addUser(bobUsername.asString(), BOB_PASSWORD)
      .addUser(andreUsername.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
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
           |			"accountId": "$bobAccountId",
           |			"ids": ["mailboxA@${domain.asString}"]
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
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$bobAccountId",
           |			"ids": ["hiring@${domain.asString}"]
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
         |				"accountId": "$bobAccountId",
         |				"revoked": ["hiring@${domain.asString}"]
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
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)
    val teamMailboxInboxId = mailboxId(server, teamMailbox.inboxPath)

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$bobAccountId",
           |			"ids": ["hiring@${domain.asString}"]
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
           |				"accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
           |			"accountId": "$bobAccountId",
           |			"ids": ["nonExistTeamMailbox@${domain.asString}"]
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
         |                "accountId": "$bobAccountId",
         |                "notRevoked": {
         |                    "nonExistTeamMailbox@${domain.asString}": {
         |                        "type": "notFound",
         |                        "description": "#TeamMailbox:team-mailbox@${domain.asString}:nonExistTeamMailbox can not be found"
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
           |			"accountId": "$bobAccountId",
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
         |                "accountId": "$bobAccountId",
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
           |			"accountId": "$bobAccountId",
           |			"ids": ["/hiring@${domain.asString}"]
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
         |				"accountId": "$bobAccountId",
         |				"notRevoked": {
         |					"/hiring@${domain.asString}": {
         |						"type": "invalidArguments",
         |						"description": "/hiring@${domain.asString} is not a Team Mailbox: Predicate failed: '/hiring@${domain.asString}' contains some invalid characters. Should be [#a-zA-Z0-9-_.@] and no longer than 320 chars."
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
           |			"accountId": "$bobAccountId",
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
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$bobAccountId",
           |			"ids": ["hiring@${domain.asString}"]
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
         |				"accountId": "$bobAccountId",
         |				"revoked": ["hiring@${domain.asString}"]
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
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    val response1 = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$bobAccountId",
           |			"ids": ["hiring@${domain.asString}"]
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
         |				"accountId": "$bobAccountId",
         |				"revoked": ["hiring@${domain.asString}"]
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
           |			"accountId": "$bobAccountId",
           |			"ids": ["hiring@${domain.asString}"]
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
         |				"accountId": "$bobAccountId",
         |				"revoked": ["hiring@${domain.asString}"]
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def mixedCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		["TeamMailbox/revokeAccess", {
           |			"accountId": "$bobAccountId",
           |			"ids": ["hiring@${domain.asString}", "nonExistTeamMailbox@${domain.asString}", "invalid"]
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
         |				"accountId": "$bobAccountId",
         |				"revoked": [
         |					"hiring@${domain.asString}"
         |				],
         |				"notRevoked": {
         |					"nonExistTeamMailbox@${domain.asString}": {
         |						"type": "notFound",
         |						"description": "#TeamMailbox:team-mailbox@${domain.asString}:nonExistTeamMailbox can not be found"
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
      .addAuthorizedUser(bobUsername, andreUsername)
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
         |	"methodCalls": [
         |		["TeamMailbox/revokeAccess", {
         |			"accountId": "$bobAccountId",
         |			"ids": ["hiring@${domain.asString}"]
         |		}, "c0"]
         |	]
         |}""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(andreUsername, ANDRE_PASSWORD)))
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
      .containsOnly(bobUsername)
  }
}
