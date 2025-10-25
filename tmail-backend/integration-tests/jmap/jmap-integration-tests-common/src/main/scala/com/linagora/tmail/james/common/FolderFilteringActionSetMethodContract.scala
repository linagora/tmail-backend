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
import java.util.Optional

import com.linagora.tmail.james.common.probe.JmapGuiceCustomProbe
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.path.json.JsonPath
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.filtering.Rule
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.{DataProbeImpl, WebAdminGuiceProbe}
import org.apache.james.webadmin.WebAdminUtils
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Test}

object FolderFilteringActionSetMethodContract {
  private var webAdminApi: RequestSpecification = _
}

trait FolderFilteringActionSetMethodContract {
  import FolderFilteringActionSetMethodContract.webAdminApi

  def errorInvalidMailboxIdMessage(value: String): String

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    FolderFilteringActionSetMethodContract.webAdminApi = WebAdminUtils
      .buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .setBasePath("/tasks")
      .build()
  }

  private def awaitTaskCompletion(taskId: String): ValidatableResponse =
    `given`()
      .spec(webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("status", Matchers.is("completed"))

  @Test
  def shouldFailWhenOmittingFilterCapability(): Unit = {
    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "mb1"}
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "error",
           |    {
           |      "type": "unknownMethod",
           |      "description":"Missing capability(ies): com:linagora:params:jmap:filter"
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def createShouldSubmitTheTaskSuccessfully(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val message: Message = Message.Builder
      .of
      .setSubject("matchedRules")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))

    // Assume user set rules via Filter/set
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(BOB,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "matchedRules"))
          .action(new Rule.Action.Builder().setMoveTo(Optional.of(new Rule.Action.MoveTo("Processed"))))
          .build)

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "${mailboxId.serialize()}"}
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
      .extract
      .body
      .asString

    val taskId: String = JsonPath.from(response).getString("methodResponses[0][1].created.c1.id")
    awaitTaskCompletion(taskId)

    val getResponse: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["$taskId"]
           |      },
           |      "#0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(getResponse)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/get",
           |  {
           |    "notFound": [],
           |    "list": [{
           |      "id": "$taskId",
           |      "status": "completed",
           |      "processedMessageCount": 1,
           |      "successfulActions": 1,
           |      "failedActions": 0,
           |      "maximumAppliedActionReached": false
           |    }]
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def createShouldSucceedWhenUserHasEmptyRules(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val message: Message = Message.Builder
      .of
      .setSubject("matchedRules")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))

    // Assume user set no rules

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "${mailboxId.serialize()}"}
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
      .extract
      .body
      .asString

    val taskId: String = JsonPath.from(response).getString("methodResponses[0][1].created.c1.id")
    awaitTaskCompletion(taskId)

    val getResponse: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["$taskId"]
           |      },
           |      "#0"
           |    ]
           |  ]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    // no action has been taken
    assertThatJson(getResponse)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/get",
           |  {
           |    "notFound": [],
           |    "list": [{
           |      "id": "$taskId",
           |      "status": "completed",
           |      "processedMessageCount": 1,
           |      "successfulActions": 0,
           |      "failedActions": 0,
           |      "maximumAppliedActionReached": false
           |    }]
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def createShouldFailOnTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
    val teamMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", DOMAIN).asString(), "marketing")
    val message: Message = Message.Builder
      .of
      .setSubject("matchedRules")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, teamMailbox.mailboxPath, AppendCommand.from(message))

    // Assume user set rules via Filter/set
    server.getProbe(classOf[JmapGuiceCustomProbe])
      .setRulesForUser(BOB,
        Rule.builder
          .id(Rule.Id.of("1"))
          .name("My first rule")
          .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "matchedRules"))
          .action(new Rule.Action.Builder().setMoveTo(Optional.of(new Rule.Action.MoveTo("Processed"))))
          .build)

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter", "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "${teamMailboxId.serialize()}"}
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/set",
           |  {
           |    "notCreated": {
           |      "c1": {
           |        "type": "notFound",
           |        "description": "${teamMailboxId.serialize()} can not be found"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def createShouldFailWhenTargetOtherUserMailbox(server: GuiceJamesServer): Unit = {
    val andreInboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "${andreInboxId.serialize()}"}
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/set",
           |  {
           |    "notCreated": {
           |      "c1": {
           |        "type": "notFound",
           |        "description": "${andreInboxId.serialize()} can not be found"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def createShouldRejectUnsupportedProperties(server: GuiceJamesServer): Unit = {
    val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "${mailboxId.serialize()}", "status": "completed"}
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/set",
           |  {
           |    "notCreated": {
           |      "c1": {
           |        "type": "invalidArguments",
           |        "description": "Unsupported properties: status"
           |      }
           |    }
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def createShouldFailWhenMissingMailboxId(): Unit = {
    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {}
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"FolderFilteringAction/set",
           |	{
           |		"notCreated": {
           |			"c1": {
           |				"type": "invalidArguments",
           |				"description": "Missing 'mailboxId' property"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def createShouldFailWhenMailboxIdInvalid(): Unit = {
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/set",
           |      {
           |        "create": {
           |          "c1": {"mailboxId": "invalidMailboxId"}
           |        }
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].notCreated.c1.type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].notCreated.c1.description", Matchers.is(s"${errorInvalidMailboxIdMessage("invalidMailboxId")}"))
  }

}