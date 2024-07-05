package com.linagora.tmail.james.common

import com.google.common.collect.ImmutableList
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxMember, TeamMailboxName, TeamMailboxProbe}
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
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

trait TeamMailboxMemberSetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(CEDRIC.asString(), "1")

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
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |                "cedric@domain.tld": {"role":"member"}
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
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).contains(TeamMailboxMember.asMember(ANDRE))
  }

  @Test
  def updateShouldPromoteExistedUserAsManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)
      .addMember(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": {"role": "manager"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).contains(TeamMailboxMember.asManager(ANDRE))
  }

  @Test
  def updateShouldAddNewManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": {"role": "manager"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).contains(TeamMailboxMember.asManager(ANDRE))
  }

  @Test
  def updateShouldRemoveMember(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)
      .addMember(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": null
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    assertThat(teamMailboxProbe.getMembers(teamMailbox).asJava).doesNotContain(TeamMailboxMember.asMember(ANDRE))
  }

  @Test
  def updateShouldUpdateMultipleMembers(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)
      .addMember(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": null,
           |                "${CEDRIC.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {
           |            "${teamMailbox.asString()}": null
           |        },
           |        "notUpdated": {}
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))

    val memberList = teamMailboxProbe.getMembers(teamMailbox).asJava
    assertThat(memberList).doesNotContain(TeamMailboxMember.asMember(ANDRE))
    assertThat(memberList).contains(TeamMailboxMember.asMember(CEDRIC))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenTeamMailboxNameIsInvalid(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "%%%": {
           |                "${ANDRE.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${CEDRIC.asString()}": {"role": "invalid"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "@domain.tld": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Invalid team member name: @domain.tld"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenTheTeamMailboxDoesNotExist(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    val nonExistedTeamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("firing"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${nonExistedTeamMailbox.asString()}": {
           |                "${CEDRIC.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${CEDRIC.asString()}": {"role": "member"},
           |                "nonexisted1@domain.tld": {"role": "member"},
           |                "nonexisted2@domain.tld": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Some users do not exist in the system: nonexisted1@domain.tld, nonexisted2@domain.tld"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUserIsNotMemberOfTheTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${CEDRIC.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${CEDRIC.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)
      .addManager(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Could not update or remove manager ${ANDRE.asString()}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenRemovingOtherManager(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))

    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addManager(teamMailbox, BOB)
      .addManager(teamMailbox, ANDRE)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": null
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "updated": {},
           |        "notUpdated": {
           |            "${teamMailbox.asString()}": {
           |                "type": "invalidPatch",
           |                "description": "Could not update or remove manager ${ANDRE.asString()}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin).withOptions(ImmutableList.of(IGNORING_ARRAY_ORDER)))
  }

  @Test
  def mixedUpdatedAndNotUpdatedCase(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    val teamMailbox2 = TeamMailbox(DOMAIN, TeamMailboxName("firing"))

    val teamMailboxProbe = server.getProbe(classOf[TeamMailboxProbe])
    teamMailboxProbe.create(teamMailbox)
      .addManager(teamMailbox, BOB)

    teamMailboxProbe.create(teamMailbox2)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "com:linagora:params:jmap:team:mailboxes"],
           |  "methodCalls": [
           |    [
           |      "TeamMailboxMember/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "update": {
           |            "${teamMailbox.asString()}": {
           |                "${ANDRE.asString()}": {"role": "member"}
           |            },
           |            "${teamMailbox2.asString()}": {
           |                "${CEDRIC.asString()}": {"role": "member"}
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
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
