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
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxMember, TeamMailboxName, TeamMailboxProbe}
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

object TeamMailboxMemberSetMethodContract {
  case class TestContext(domain: Domain) {
    val bobUsername: Username = Username.fromLocalPartWithDomain("bob", domain)
    val andreUsername: Username = Username.fromLocalPartWithDomain("andre", domain)
    val cedricUsername: Username = Username.fromLocalPartWithDomain("cedric", domain)
    val bobAccountId: String = Hashing.sha256().hashString(bobUsername.asString(), StandardCharsets.UTF_8).toString
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

trait TeamMailboxMemberSetMethodContract {
  def domain: Domain = TeamMailboxMemberSetMethodContract.currentContext.get().domain

  def bobUsername: Username = TeamMailboxMemberSetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = TeamMailboxMemberSetMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = TeamMailboxMemberSetMethodContract.currentContext.get().andreUsername

  def cedricUsername: Username = TeamMailboxMemberSetMethodContract.currentContext.get().cedricUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    TeamMailboxMemberSetMethodContract.currentContext.set(TeamMailboxMemberSetMethodContract.TestContext(Domain.of(s"domain$uniqueSuffix.tld")))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(domain.asString)
      .addUser(bobUsername.asString(), BOB_PASSWORD)
      .addUser(andreUsername.asString(), ANDRE_PASSWORD)
      .addUser(cedricUsername.asString(), "1")

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
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "ids": null
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
      .body("", jsonEquals(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    [
           |      "error",
           |      {
           |        "type": "unknownMethod",
           |        "description": "Missing capability(ies): com:linagora:params:jmap:team:mailboxes"
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin))


  @Test
  def shouldFailWhenWrongAccountId(): Unit =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "unknownAccountId",
           |        "update": {
           |            "team-mailbox-name": {
           |                "cedric@${domain.asString}": {"role":"member"}
           |            }
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
  def updateShouldAddNewMember(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).contains(TeamMailboxMember.asMember(andreUsername))
  }

  @Test
  def updateShouldPromoteExistedUserAsManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)
      .addMember(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": {"role": "manager"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).contains(TeamMailboxMember.asManager(andreUsername))
  }

  @Test
  def updateShouldAddNewManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": {"role": "manager"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).contains(TeamMailboxMember.asManager(andreUsername))
  }

  @Test
  def updateShouldRemoveMember(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)
      .addMember(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": null
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).doesNotContain(TeamMailboxMember.asMember(andreUsername))
  }

  @Test
  def updateShouldUpdateMultipleMembers(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)
      .addMember(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": null,
           |                "${cedricUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    val memberList = teamMailboxProbe.getMembers(teamMailbox).asJava
    assertThat(memberList).doesNotContain(TeamMailboxMember.asMember(andreUsername))
    assertThat(memberList).contains(TeamMailboxMember.asMember(cedricUsername))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenTeamMailboxNameIsInvalid(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "%%%": {
           |                "${andreUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "%%%": {
           |                "type": "invalidPatch",
           |                "description": "Invalid teamMailboxName"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenRoleIsInvalid(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${cedricUsername.asString()}": {"role": "invalid"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Invalid role: invalid"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenMemberNameIsInvalid(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "@${domain.asString}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Invalid team member name: @${domain.asString}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenTheTeamMailboxDoesNotExist(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    val nonExistedTeamMailbox = TeamMailbox(domain, TeamMailboxName("firing"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${nonExistedTeamMailbox.asString()}": {
           |                "${cedricUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${nonExistedTeamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Team mailbox is not found or not a member of the mailbox"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenMemberUserDoesNotExistInTheSystem(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${cedricUsername.asString()}": {"role": "member"},
           |                "nonexisted1@${domain.asString}": {"role": "member"},
           |                "nonexisted2@${domain.asString}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Some users do not exist in the system: nonexisted1@${domain.asString}, nonexisted2@${domain.asString}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUserIsNotMemberOfTheTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${cedricUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Team mailbox is not found or not a member of the mailbox"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUserIsNotManagerOfTheTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, bobUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${cedricUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Not manager of teamMailbox ${teamMailbox.asString()}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUpdatingOtherManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)
      .addManager(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Could not update or remove manager ${andreUsername.asString()}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenRemovingOtherManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, bobUsername)
      .addManager(teamMailbox, andreUsername)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": null
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Could not update or remove manager ${andreUsername.asString()}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def mixedUpdatedAndNotUpdatedCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(domain, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(domain, TeamMailboxName("firing"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    teamMailboxProbe.create(teamMailbox)
      .addManager(teamMailbox, bobUsername)

    teamMailboxProbe.create(teamMailbox2)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "${bobAccountId}",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${andreUsername.asString()}": {"role": "member"}
           |            },
           |            "${teamMailbox2.asString()}": {
           |                "${cedricUsername.asString()}": {"role": "member"}
           |            }
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
           |    "TeamMailboxMember/set",
           |    {
           |        "accountId": "${bobAccountId}",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {
           |            "${teamMailbox2.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Team mailbox is not found or not a member of the mailbox"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }
}
