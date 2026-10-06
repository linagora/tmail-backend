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

import com.google.common.collect.ImmutableList
import com.google.common.hash.Hashing
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

object TeamMailboxMemberGetMethodContract {
  case class TestContext(domain: Domain) {
    val bobUsername: Username = Username.fromLocalPartWithDomain("bob", domain)
    val andreUsername: Username = Username.fromLocalPartWithDomain("andre", domain)
    val bobAccountId: String = Hashing.sha256().hashString(bobUsername.asString(), StandardCharsets.UTF_8).toString
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait TeamMailboxMemberGetMethodContract {
  def domain: Domain = TeamMailboxMemberGetMethodContract.currentContext.get().domain

  def bobUsername: Username = TeamMailboxMemberGetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = TeamMailboxMemberGetMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = TeamMailboxMemberGetMethodContract.currentContext.get().andreUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    TeamMailboxMemberGetMethodContract.currentContext.set(TeamMailboxMemberGetMethodContract.TestContext(Domain.of(s"domain$uniqueSuffix.tld")))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(domain.asString)
      .addUser(bobUsername.asString(), BOB_PASSWORD)
      .addUser(andreUsername.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def missingTeamMailboxesCapabilityShouldFail(): Unit =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
           |			{
           |				"accountId": "${bobAccountId}",
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
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
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
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
           |			{
           |				"accountId": "${bobAccountId}",
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
           |        "accountId": "${bobAccountId}",
           |        "list": [],
           |        "notFound": []
           |    },
           |    "c1"
           |]""".stripMargin))

  @Test
  def fetchNullIdsShouldReturnMembersOfAllMailboxes(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(domain, TeamMailboxName("firing"))
    val teamMailbox3 = TeamMailbox(domain, TeamMailboxName("external"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)
      .addManager(teamMailbox, andreUsername)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox2)
      .addMember(teamMailbox2, bobUsername)
      .addManager(teamMailbox2, andreUsername)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox3)
      .addMember(teamMailbox3, andreUsername)

    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |	"methodCalls": [
           |		[
           |			"TeamMailboxMember/get",
           |			{
           |				"accountId": "${bobAccountId}",
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
           |        "accountId": "${bobAccountId}",
           |        "list": [
           |                  {
           |                      "id": "${teamMailbox.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@${domain.asString}": {"role":"member"},
           |                          "andre@${domain.asString}": {"role":"manager"}
           |                      }
           |                  },
           |                  {
           |                      "id": "${teamMailbox2.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@${domain.asString}": {"role":"member"},
           |                          "andre@${domain.asString}": {"role":"manager"}
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
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(domain, TeamMailboxName("firing"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox2)
      .addMember(teamMailbox2, bobUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/get",
           |      {
           |        "accountId": "${bobAccountId}",
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
           |        "accountId": "${bobAccountId}",
           |        "list": [
           |                  {
           |                      "id": "${teamMailbox.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@${domain.asString}": {"role":"member"}
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
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/get",
           |      {
           |        "accountId": "${bobAccountId}",
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
           |        "accountId": "${bobAccountId}",
           |        "list": [],
           |        "notFound": [ "${teamMailbox.mailboxName.asString()}" ]
           |    },
           |    "c1"
           |]""".stripMargin))
  }

  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(domain, TeamMailboxName("firing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox2)
      .addMember(teamMailbox2, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/get",
           |      {
           |        "accountId": "${bobAccountId}",
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
           |        "accountId": "${bobAccountId}",
           |        "list": [
           |                  {
           |                      "id": "${teamMailbox.mailboxName.asString()}",
           |                      "members": {
           |                          "bob@${domain.asString}": {"role":"member"}
           |                      }
           |                  }
           |        ],
           |        "notFound": [ "${teamMailbox2.mailboxName.asString()}", "notFound" ]
           |    },
           |    "c1"
           |]""".stripMargin))
  }
}
