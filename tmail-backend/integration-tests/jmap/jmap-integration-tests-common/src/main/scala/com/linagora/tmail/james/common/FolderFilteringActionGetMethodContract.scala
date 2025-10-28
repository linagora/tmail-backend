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

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Inject}
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
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.api.filtering.Rule.Action
import org.apache.james.jmap.api.filtering.{Rule, Rules, Version}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.task.{TaskId, TaskManager}
import org.apache.james.utils.{DataProbeImpl, GuiceProbe, WebAdminGuiceProbe}
import org.apache.james.webadmin.WebAdminUtils
import org.apache.james.webadmin.data.jmap.{RunRulesOnMailboxService, RunRulesOnMailboxTask}
import org.apache.james.webadmin.validation.MailboxName
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

class RunRulesTaskProbeModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[RunRulesTaskProbe])
  }
}

class RunRulesTaskProbe @Inject()(taskManager: TaskManager,
                                  service: RunRulesOnMailboxService) extends GuiceProbe {
  def submit(username: Username, mailboxName: MailboxName, rules: Rules): TaskId = {
    val task = new RunRulesOnMailboxTask(username, mailboxName, rules, service)
    taskManager.submit(task)
  }
}

object FolderFilteringActionGetMethodContract {
  private var webAdminApi: RequestSpecification = _
}

trait FolderFilteringActionGetMethodContract {
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

    FolderFilteringActionGetMethodContract.webAdminApi = WebAdminUtils
      .buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .setBasePath("/tasks")
      .build()
  }

  private def createAndRunRulesTask(server: GuiceJamesServer, user: UserCredential, mailboxName: String, moveToMailbox: String): String = {
    val username = user.username

    val condition: Rule.Condition = Rule.Condition.of(
      Rule.Condition.FixedField.SUBJECT,
      Rule.Condition.Comparator.CONTAINS,
      "matchedRules")
    val actionBuilder: Action.Builder = new Rule.Action.Builder()
      .setMoveTo(Optional.of(new Rule.Action.MoveTo(moveToMailbox)))
    val rule = Rule.builder()
      .id(Rule.Id.of("r1"))
      .name("move")
      .conditionGroup(condition)
      .action(actionBuilder)
      .build()
    val rules = new Rules(java.util.List.of(rule), Version.INITIAL)

    val taskId: String = server.getProbe(classOf[RunRulesTaskProbe])
      .submit(username, new MailboxName(mailboxName), rules)
      .asString()

    awaitTaskCompletion(taskId)
    taskId
  }

  private def awaitTaskCompletion(taskId: String): ValidatableResponse =
    `given`()
      .spec(FolderFilteringActionGetMethodContract.webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("status", Matchers.is("completed"))

  private def createMessagesRestoreTask(userCredential: UserCredential = UserCredential(BOB, BOB_PASSWORD), subjectQuery: String) = {
    val taskId: String = `given`
      .auth.basic(userCredential.username.asString(), userCredential.password)
      .body(
        s"""{
           |	"using": [
           |		"urn:ietf:params:jmap:core",
           |		"com:linagora:params:jmap:messages:vault"
           |	],
           |	"methodCalls": [
           |		[
           |			"EmailRecoveryAction/set",
           |			{
           |				"create": {
           |					"clientId1": {
           |						"subject": "$subjectQuery"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.clientId1.id").toString
    taskId
  }

  @Test
  def shouldFailWhenOmittingFilterCapability(): Unit = {
    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["2034-495-05857-57abcd-0876664"]
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
  def shouldFailWhenNullIds(): Unit = {
    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "error",
           |  {
           |    "type": "invalidArguments",
           |    "description": "'/ids' property need to be an array"
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  def shouldFailWhenOmittingIds(): Unit = {
    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      { },
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
           |  "error",
           |  {
           |    "type": "invalidArguments",
           |    "description": "Missing '/ids' property"
           |  },
           |  "c1"
           |]""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldReturnAllPropertiesByDefault(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val message: Message = Message.Builder
      .of
      .setSubject("matchedRules")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString, MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId

    val taskId: String = createAndRunRulesTask(server, UserCredential(BOB, BOB_PASSWORD), MailboxPath.inbox(BOB).getName, "Processed")

    val response: String = `given`
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

    assertThatJson(response)
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
  def getNonExistingTaskShouldReturnNotFound(): Unit = {
    val response = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["77731634-ea82-4a1a-bd4c-9f8ece4f66c7"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""["FolderFilteringAction/get",
           |  {
           |    "notFound": ["77731634-ea82-4a1a-bd4c-9f8ece4f66c7"],
           |    "list": []
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))

    val taskId: String = createAndRunRulesTask(server, UserCredential(BOB, BOB_PASSWORD), MailboxPath.inbox(BOB).getName, "Processed")

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["$taskId", "notFoundTaskId"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/get",
           |  {
           |    "notFound": ["notFoundTaskId"],
           |    "list": [{
           |      "id": "$taskId",
           |      "status": "completed",
           |      "processedMessageCount": 0,
           |      "successfulActions": 0,
           |      "failedActions": 0,
           |      "maximumAppliedActionReached": false
           |    }]
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def shouldFilterProperties(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    val taskId: String = createAndRunRulesTask(server, UserCredential(BOB, BOB_PASSWORD), MailboxPath.inbox(BOB).getName, "Processed")

    val response = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["$taskId"],
           |        "properties": ["status"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/get",
           |  {
           |    "notFound": [],
           |    "list": [{
           |      "id": "$taskId",
           |      "status": "completed"
           |    }]
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def shouldNotReturnFolderFilteringActionOfOtherUsers(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    val andreTaskId: String = createAndRunRulesTask(server, UserCredential(ANDRE, ANDRE_PASSWORD), MailboxPath.inbox(ANDRE).getName, "Processed")

    val response: String = `given`
      .auth.basic(BOB.asString(), BOB_PASSWORD)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["$andreTaskId"],
           |        "properties": ["status"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/get",
           |  {
           |    "notFound": ["$andreTaskId"],
           |    "list": []
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def shouldNotReturnNonFolderFilteringTask(): Unit = {
    val emailRecoveryActionTaskId: String = createMessagesRestoreTask(subjectQuery = "whatever")

    val response: String = `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:filter"],
           |  "methodCalls": [
           |    ["FolderFilteringAction/get",
           |      {
           |        "ids": ["$emailRecoveryActionTaskId"],
           |        "properties": ["status"]
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

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |  "FolderFilteringAction/get",
           |  {
           |    "notFound": ["$emailRecoveryActionTaskId"],
           |    "list": []
           |  },
           |  "#0"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnFilteringActionOnTeamMailbox(server: GuiceJamesServer): Unit = {
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

    val setResponse: String = `given`
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

    val taskId: String = JsonPath.from(setResponse).getString("methodResponses[0][1].created.c1.id")
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
}
