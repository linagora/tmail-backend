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

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Inject}
import com.linagora.tmail.james.common.EmailRecoveryActionSetMethodContract.{DELETED_MESSAGE_CONTENT, TIME_FORMATTER, taskAwaitBasePath, webAdminApi}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{MailAddress, MaybeSender, Username}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.task.{MemoryReferenceTask, Task, TaskId, TaskManager}
import org.apache.james.utils.{DataProbeImpl, GuiceProbe, WebAdminGuiceProbe}
import org.apache.james.vault.search.Query
import org.apache.james.vault.{DeletedMessage, DeletedMessageVault}
import org.apache.james.webadmin.WebAdminUtils
import org.apache.mailet.base.MailAddressFixture.{RECIPIENT1, RECIPIENT2, SENDER, SENDER2}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers
import org.junit.jupiter.api.{AfterEach, BeforeEach, Nested, Tag, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import play.api.libs.json.{JsString, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class DeletedMessageVaultProbe @Inject()(vault: DeletedMessageVault) extends GuiceProbe {
  def append(deletedMessage: DeletedMessage, mimeMessage: InputStream): Unit =
    SMono(vault.append(deletedMessage, mimeMessage)).block()

  def deletedAllMessages(username: Username): Unit =
    SFlux(vault.search(username, Query.ALL))
      .flatMap(deletedMessage => vault.delete(username, deletedMessage.getMessageId))
      .`then`()
      .block()
}

class DeletedMessageVaultProbeModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[DeletedMessageVaultProbe])
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[TaskManagerProbe])
  }
}

class TaskManagerProbe @Inject()(taskManager: TaskManager) extends GuiceProbe {
  def submitTask(task: Task): Unit = taskManager.submit(task)
}

object EmailRecoveryActionSetMethodContract {
  private var webAdminApi: RequestSpecification = _
  private val taskAwaitBasePath: String = "/tasks"

  val DELETED_MESSAGE_CONTENT: Array[Byte] = "header: value\r\n\r\ncontent".getBytes(StandardCharsets.UTF_8)
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
  val RESTORATION_HORIZON_SPAN_IN_DAYS: Int = 14

  def creationSetInvalidRequestList: Stream[Arguments] = {
    val template: String =
      """{
        |	"using": [
        |		"urn:ietf:params:jmap:core",
        |		"com:linagora:params:jmap:messages:vault"
        |	],
        |	"methodCalls": [
        |		[
        |			"EmailRecoveryAction/set",
        |			{
        |				"create": {
        |					"clientId1": {%s}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val invalidDeletedBefore: String = """"deletedBefore": "deletedBefore should be a UTC date""""
    val invalidDeletedBeforeNotUTC: String = """"deletedBefore": "2022-02-01""""
    val invalidDeletedAfter: String = """"deletedAfter": "deletedAfter should be a UTC date""""
    val invalidReceivedBefore: String = """"receivedBefore": "receivedBefore should be a UTC date""""
    val invalidReceivedAfter: String = """"receivedAfter": "receivedAfter should be a UTC date""""
    val invalidSubject: String = """"subject": true"""
    val invalidSenderBoolean: String = """"sender": true"""
    val invalidSenderNotAMail: String = """"sender": "abc@""""
    val invalidRecipients: String = """"recipients": true"""
    val invalidRecipientsNotAMails: String = """"recipients": ["abc@", "def@"]"""
    val invalidHasAttachment: String = """"hasAttachment": "hasAttachment should be a boolean""""

    Stream.of(
      Arguments.of(String.format(template, invalidDeletedBefore)),
      Arguments.of(String.format(template, invalidDeletedBeforeNotUTC)),
      Arguments.of(String.format(template, invalidDeletedAfter)),
      Arguments.of(String.format(template, invalidReceivedBefore)),
      Arguments.of(String.format(template, invalidReceivedAfter)),
      Arguments.of(String.format(template, invalidSubject)),
      Arguments.of(String.format(template, invalidSenderBoolean)),
      Arguments.of(String.format(template, invalidSenderNotAMail)),
      Arguments.of(String.format(template, invalidRecipients)),
      Arguments.of(String.format(template, invalidRecipientsNotAMails)),
      Arguments.of(String.format(template, invalidHasAttachment)))
  }
}

trait EmailRecoveryActionSetMethodContract {
  import EmailRecoveryActionSetMethodContract.RESTORATION_HORIZON_SPAN_IN_DAYS

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

    webAdminApi = WebAdminUtils.buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .setBasePath(taskAwaitBasePath)
      .build()
  }

  @AfterEach
  def afterEach(server: GuiceJamesServer): Unit = {
    val deletedMessageVaultProbe = server.getProbe(classOf[DeletedMessageVaultProbe])
    deletedMessageVaultProbe.deletedAllMessages(BOB)
    deletedMessageVaultProbe.deletedAllMessages(ANDRE)
  }

  def randomMessageId: MessageId

  @Test
  def validSetCreationRequestShouldReturnTaskId(): Unit = {
    val response: String = `given`
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
           |						"deletedBefore": "2016-06-09T01:07:06Z",
           |						"deletedAfter": "2017-07-09T01:07:06Z",
           |						"receivedBefore": "2018-08-09T01:07:06Z",
           |						"receivedAfter": "2019-09-09T01:07:06Z",
           |						"hasAttachment": true,
           |						"subject": "Simple topic",
           |						"sender": "bob@domain.tld",
           |						"recipients": [
           |							"alice@domain.tld",
           |							"andre@example.tld"
           |						]
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
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"created": {
           |			"clientId1": {
           |				"id": "$${json-unit.ignore}"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def creationSetRequestShouldReturnTaskIdWasCreatedByTaskManager(): Unit = {
    val taskId: String = `given`
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
           |						"deletedBefore": "2016-06-09T01:07:06Z"
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
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.clientId1.id").toString

    `given`()
      .spec(webAdminApi)
      .get(taskId)
    .`then`()
      .statusCode(SC_OK)
      .body("taskId", Matchers.is(taskId))
      .body("type", Matchers.is("deleted-messages-restore"))
  }

  @ParameterizedTest
  @MethodSource(value = Array("creationSetInvalidRequestList"))
  def creationSetRequestShouldReturnNotCreatedWhenInvalidRequest(invalidRequest: String): Unit = {
    `given`
      .body(invalidRequest)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].notCreated.clientId1.type", Matchers.is("invalidArguments"))
  }

  @Test
  def creationSetRequestShouldReturnNotCreatedWhenUnknownProperties(): Unit = {
    val response: String = `given`
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
           |						"unknownProperty": "2016-06-09T01:07:06Z"
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
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"notCreated": {
           |			"clientId1": {
           |				"type": "invalidArguments",
           |				"description": "Unknown properties: unknownProperty"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def setShouldFailWhenOmittingOneCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": [ "urn:ietf:params:jmap:core"	],
           |	"methodCalls": [
           |		[
           |			"EmailRecoveryAction/set",
           |			{
           |				"create": {
           |					"clientId1": {
           |						"deletedBefore": "2016-06-09T01:07:06Z"
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
           |      "description":"Missing capability(ies): com:linagora:params:jmap:messages:vault"
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenOmittingAllCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": [ ],
           |	"methodCalls": [
           |		[
           |			"EmailRecoveryAction/set",
           |			{
           |				"create": {
           |					"clientId1": {
           |						"deletedBefore": "2016-06-09T01:07:06Z"
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
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description":"Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:messages:vault"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def setShouldFailWhenInvalidCreationId(): Unit = {
    `given`
      .body(
        s"""{
           |	"using": [ "urn:ietf:params:jmap:core",
           |             "com:linagora:params:jmap:messages:vault" ],
           |	"methodCalls": [
           |		[
           |			"EmailRecoveryAction/set",
           |			{
           |				"create": {
           |					"clientId1@@": {
           |						"deletedBefore": "2016-06-09T01:07:06Z"
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
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].description", Matchers.containsString("contains some invalid characters. Should be [#a-zA-Z0-9-_]"))
  }

  @Nested
  @Tag(CategoryTags.BASIC_FEATURE)
  class CreationSetBySubjectQueryContract {
    @Test
    def restoreShouldNotAppendMessageToMailboxWhenSubjectDoesntContains(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        subject = "subject contains should match")

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val subjectQuery: String = "apache james"
      val taskId: String = `given`
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }

    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingSubjectContains(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        subject = "subject contains should match")

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val subjectQuery: String = "subject contains"
      val taskId: String = `given`
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }
  }

  @Nested
  class CreationSetByDeletedDateQueryContract {
    def createDefaultRequest(): String = {
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
         |					"clientId1": {}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin
    }

    def createRequestWithCriterionDeletedBefore(deletedBefore: String): String = {
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
         |						"deletedBefore": "$deletedBefore"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin
    }

    def createRequestWithCriterionDeletedAfter(deletedAfter: String): String = {
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
         |						"deletedAfter": "$deletedAfter"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin
    }

    def createDeletedMail(server: GuiceJamesServer, deletionDate: ZonedDateTime) = {

      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        deletionDate = deletionDate)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage, new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))
    }

    private def sendRequestAndGetResponse(request: String): String = {
      `given`
        .body(request)
      .when
        .post
      .`then`
        .extract()
        .jsonPath()
        .get("methodResponses[0][1].created.clientId1.id").toString
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenDeletionDateIsOutOfUserLimitAndNoDeletionDateIsRequired(server: GuiceJamesServer): Unit = {
      val deletionDateOutOfHorizon = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS + 1)
      createDeletedMail(server, deletionDateOutOfHorizon)
      
      val request: String = createDefaultRequest()

      val taskId: String = sendRequestAndGetResponse(request)
      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB))
        .isEmpty()
    }

    @Test
    def restoreShouldAppendMessageToMailboxWhenDeletionDateIsWithinUserLimitAndNoDeletionDateIsRequired(server: GuiceJamesServer): Unit = {
      val deletionDateWithinHorizon = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS - 1)
      createDeletedMail(server, deletionDateWithinHorizon)
      
      val request: String = createDefaultRequest()

      val taskId: String = sendRequestAndGetResponse(request)
      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB))
        .hasSize(1)
    }

    @Test
    def restoreShouldAppendMessageToMailboxWhenDeletionDateIsWithinUserLimitAndMatchingDeletionDateBeforeOrEquals(server: GuiceJamesServer): Unit = {
      val deletionDateWithinHorizon = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS - 1)
      createDeletedMail(server, deletionDateWithinHorizon)

      val matchingDate = deletionDateWithinHorizon.plusHours(1).format(TIME_FORMATTER)
      val request: String = createRequestWithCriterionDeletedBefore(matchingDate)

      val taskId: String = sendRequestAndGetResponse(request)
      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB))
        .hasSize(1)
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenDeletionDateIsWithinUserLimitAndNotMatchingDeletionDateBeforeOrEquals(server: GuiceJamesServer): Unit = {
      val deletionDateWithinHorizon = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS - 1)
      createDeletedMail(server, deletionDateWithinHorizon)

      val nonMatchingDate = deletionDateWithinHorizon.minusHours(1).format(TIME_FORMATTER)
      val request: String = createRequestWithCriterionDeletedBefore(nonMatchingDate)

      val taskId: String = sendRequestAndGetResponse(request)
      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB))
        .isEmpty()
    }

    @Test
    def restoreShouldAppendMessageToMailboxWhenDeletionDateIsWithinUserLimitAndMatchingDeletionDateAfterOrEquals(server: GuiceJamesServer): Unit = {
      val deletionDateWithinHorizon = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS - 1)
      createDeletedMail(server, deletionDateWithinHorizon)

      val matchingDate = deletionDateWithinHorizon.minusHours(1).format(TIME_FORMATTER)
      val request: String = createRequestWithCriterionDeletedAfter(matchingDate)

      val taskId: String = sendRequestAndGetResponse(request)
      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB))
        .hasSize(1)
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenDeletionDateIsWithinUserLimitAndNotMatchingDeletionDateAfterOrEquals(server: GuiceJamesServer): Unit = {
      val deletionDateWithinHorizon = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS - 1)
      createDeletedMail(server, deletionDateWithinHorizon)

      val nonMatchingDate = deletionDateWithinHorizon.plusHours(1).format(TIME_FORMATTER)
      val request: String = createRequestWithCriterionDeletedAfter(nonMatchingDate)

      val taskId: String = sendRequestAndGetResponse(request)
      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB))
        .isEmpty()
    }
  }

  @Nested
  class CreationSetByReceivedDateQueryContract {
    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingDeliveryDateBeforeOrEquals(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val receivedBefore = deletedMessage.getDeliveryDate.plusHours(1)
        .format(TIME_FORMATTER)

      val taskId: String = `given`
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
             |						"receivedBefore": "$receivedBefore"
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenNotMatchingDeliveryDateBeforeOrEquals(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val receivedBefore = deletedMessage.getDeliveryDate.minusHours(1)
        .format(TIME_FORMATTER)

      val taskId: String = `given`
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
             |						"receivedBefore": "$receivedBefore"
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }

    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingDeliveryDateAfterOrEquals(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val receivedAfter = deletedMessage.getDeliveryDate.plusHours(1)
        .format(TIME_FORMATTER)

      val taskId: String = `given`
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
             |						"receivedAfter": "$receivedAfter"
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenNotMatchingDeliveryDateAfterOrEquals(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val receivedAfter = deletedMessage.getDeliveryDate.minusHours(1)
        .format(TIME_FORMATTER)

      val taskId: String = `given`
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
             |						"receivedAfter": "$receivedAfter"
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }
  }

  @Nested
  class CreationSetByRecipientsQueryContract {
    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingRecipientContains(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        recipients = Seq(RECIPIENT1))

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val recipient = RECIPIENT1.asString()

      val taskId: String = `given`
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
             |						"recipients": ["$recipient"]
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenMatchingRecipientsDoNotContain(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        recipients = Seq(RECIPIENT1))

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val recipient = RECIPIENT2.asString()

      val taskId: String = `given`
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
             |						"recipients": ["$recipient"]
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }
  }

  @Nested
  class CreationSetBySenderQueryContract {
    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingSenderEquals(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        sender = MaybeSender.of(SENDER))

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val senderQuery = SENDER.asString()

      val taskId: String = `given`
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
             |						"sender": "$senderQuery"
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }

    @Test
    def restoreShouldNOTAppendMessageToMailboxWhenMatchingSenderDoesntEquals(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        sender = MaybeSender.of(SENDER))

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val senderQuery = SENDER2.asString()

      val taskId: String = `given`
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
             |						"sender": "$senderQuery"
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }
  }

  @Nested
  class CreationSetByHasAttachmentQueryContract {

    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingNoAttachment(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        hasAttachment = false)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val hasAttachmentQuery = false

      val taskId: String = `given`
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
             |						"hasAttachment": $hasAttachmentQuery
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }

    @Test
    def restoreShouldAppendMessageToMailboxWhenMatchingHasAttachment(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        hasAttachment = true)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val hasAttachmentQuery = true

      val taskId: String = `given`
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
             |						"hasAttachment": $hasAttachmentQuery
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenMatchingHasNoAttachment(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        hasAttachment = false)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val hasAttachmentQuery = true

      val taskId: String = `given`
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
             |						"hasAttachment": $hasAttachmentQuery
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }
  }

  @Nested
  class CreationSetByMultipleCriteriaQueryContract {
    @Test
    def restoreShouldAppendMessageToMailboxWhenAllCriteriaAreMatched(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        subject = "subject contains should match",
        sender = MaybeSender.of(SENDER),
        hasAttachment = true)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val taskId: String = `given`
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
             |						"hasAttachment": true,
             |            "subject": "subject contains",
             |            "sender": "${SENDER.asString}",
             |            "deletedBefore": "${deletedMessage.getDeletionDate.plusHours(1).format(TIME_FORMATTER)}"
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

      awaitRestoreTaskCompleted(taskId)

      assertListAllMessageHasSize(server, BOB, 1)
    }

    @Test
    def restoreShouldNotAppendMessageToMailboxWhenASingleCriterionDoesntMatch(server: GuiceJamesServer): Unit = {
      val mailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
        .createMailbox(MailboxPath.inbox(BOB))

      val deletedMessage: DeletedMessage = templateDeletedMessage(
        messageId = randomMessageId,
        mailboxId = mailboxId,
        subject = "subject contains should match",
        sender = MaybeSender.of(SENDER),
        hasAttachment = true)

      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(deletedMessage,
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

      assertThat(listAllMessageResult(server, BOB)).isEmpty()

      val taskId: String = `given`
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
             |						"hasAttachment": true,
             |            "subject": "subject contains",
             |            "sender": "${SENDER2.asString}",
             |            "deletedBefore": "${deletedMessage.getDeletionDate.plusHours(1).format(TIME_FORMATTER)}"
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

      awaitRestoreTaskCompleted(taskId)

      assertThat(listAllMessageResult(server, BOB)).isEmpty()
    }
  }

  @Test
  def creationSetShouldNotDeleteExistingMessagesInTheUserMailbox(server: GuiceJamesServer): Unit = {
    val bobMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB),
        AppendCommand.from(Message.Builder
          .of
          .setSubject("test")
          .setBody("testmail", StandardCharsets.UTF_8)
          .build))

    val deletedMessage: DeletedMessage = templateDeletedMessage(
      mailboxId = bobMailboxId,
      subject = "subject contains should match")

    server.getProbe(classOf[DeletedMessageVaultProbe])
      .append(deletedMessage,
        new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

    assertListAllMessageHasSize(server, BOB, 1)

    val taskId: String = `given`
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
           |						"subject": "subject contains"
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

    awaitRestoreTaskCompleted(taskId)

    assertListAllMessageHasSize(server, BOB, 2)
  }

  @Test
  def creationSetShouldNotRestoreAppendMessagesToAnOtherUserMailbox(server: GuiceJamesServer): Unit = {
    val bobMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(ANDRE))

    val deletedMessage: DeletedMessage = templateDeletedMessage(
      mailboxId = bobMailboxId,
      subject = "subject contains should match")

    server.getProbe(classOf[DeletedMessageVaultProbe])
      .append(deletedMessage,
        new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))

    assertThat(listAllMessageResult(server, BOB)).isEmpty()
    assertThat(listAllMessageResult(server, ANDRE)).isEmpty()

    val subjectQuery: String = "subject contains"
    val taskId: String = `given`
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

    awaitRestoreTaskCompleted(taskId)

    assertListAllMessageHasSize(server, BOB, 1)
    assertThat(listAllMessageResult(server, ANDRE)).isEmpty()
  }

  @Test
  def creationSetShouldRestrictTheMaxEmailRecoveryPerRequest(server: GuiceJamesServer): Unit = {
    val bobMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    Range.Int.inclusive(1, 15, 1).foreach(_ => {
      server.getProbe(classOf[DeletedMessageVaultProbe])
        .append(templateDeletedMessage(
          mailboxId = bobMailboxId,
          subject = "subject contains should match"),
          new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))
    })

    assertThat(listAllMessageResult(server, BOB)).isEmpty()

    val subjectQuery: String = "subject contains"
    val taskId: String = `given`
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

    val jmapMaxEmailRecoveryPerRequest: Int = 6
    `given`()
      .spec(webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("additionalInformation.successfulRestoreCount", Matchers.is(jmapMaxEmailRecoveryPerRequest))
      .body("additionalInformation.errorRestoreCount", Matchers.is(0))
  }

  @Test
  def creationSetShouldSupportBackReferences(): Unit = {
    val response: String = `given`
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
           |					"clientId1": { "subject": "subject" }
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Core/echo",
           |			{ "arg1": "#clientId1" },
           |			"c2"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses")
      .isEqualTo(s"""[
                   |	[
                   |		"EmailRecoveryAction/set",
                   |		{
                   |			"created": {
                   |				"clientId1": {
                   |					"id": "$${json-unit.ignore}"
                   |				}
                   |			}
                   |		},
                   |		"c1"
                   |	],
                   |	[
                   |		"Core/echo",
                   |		{
                   |			"arg1": "$${json-unit.ignore}"
                   |		},
                   |		"c2"
                   |	]
                   |]""".stripMargin)

    val taskId: String = (((Json.parse(response) \\ "methodResponses")
      .head \\ "created")
      .head \\ "id")
      .head.asInstanceOf[JsString].value

    val backReferenceValue: String = ((Json.parse(response) \\ "methodResponses")
      .head \\ "arg1")
      .head.asInstanceOf[JsString].value

    assertThat(taskId)
      .isEqualTo(backReferenceValue)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def updateStatusCanceledShouldCancelTask(server: GuiceJamesServer): Unit = {
    // It make the `deleted-messages-restore` task will waiting
    // when the hanging task to be completed
    val hangingTask : Task = new MemoryReferenceTask(() => {
      Thread.sleep(2000)
      Task.Result.COMPLETED
    })

    server.getProbe(classOf[TaskManagerProbe])
      .submitTask(hangingTask)

    val taskId: String = newCreationSetRequestAndGetTaskId()

    val response: String = `given`
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
           |				"update": { "$taskId": { "status": "canceled" } }
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"updated": {
           |			"$taskId": {}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)

    `given`()
      .spec(webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("status", Matchers.is("canceled"))
  }

  @Test
  def updateShouldReturnNotUpdatedWhenInvalidStatus(server: GuiceJamesServer): Unit = {
    val taskId: String = newCreationSetRequestAndGetTaskId()

    val response: String = `given`
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
           |				"update": { "$taskId": { "status": "invalid" } }
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"notUpdated": {
           |			"$taskId": {
           |				"type": "invalidArguments",
           |				"description": "Invalid status 'invalid'"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def cancelCompletedTaskShouldReturnInvalidStatusError(): Unit = {
    val taskId: String = newCreationSetRequestAndGetTaskId()

    `given`()
      .spec(webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("status", Matchers.is("completed"))

    val response: String = `given`
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
           |				"update": { "$taskId": { "status": "canceled" } }
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
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "EmailRecoveryAction/set",
           |    {
           |        "notUpdated": {
           |            "$taskId": {
           |                "type": "invalidStatus",
           |                "description": "The task was in status `completed` and cannot be canceled",
           |                "properties": [
           |                    "status"
           |                ]
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenTaskIdDoesNotFound(server: GuiceJamesServer): Unit = {
    val taskId: String = TaskId.generateTaskId().asString()

    val response: String = `given`
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
           |				"update": { "$taskId": { "status": "canceled" } }
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"notUpdated": {
           |			"$taskId": {
           |				"type": "notFound",
           |				"description": "Task not found"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenMissingStatus(server: GuiceJamesServer): Unit = {
    val taskId: String = TaskId.generateTaskId().asString()

    val response: String = `given`
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
           |				"update": { "$taskId": {  } }
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"notUpdated": {
           |			"$taskId": {
           |				"type": "invalidArguments",
           |				"description": "Missing '/status' property"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenInvalidTaskId(server: GuiceJamesServer): Unit = {
    val taskId: String = TaskId.generateTaskId().asString()

    val response: String = `given`
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
           |				"update": { "!123": { "status": "canceled" } }
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"error",
           |	{
           |		"type": "invalidArguments",
           |		"description": "$${json-unit.ignore}"
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUserIsNotOwnerOfTaskId(server: GuiceJamesServer) : Unit = {
    val taskIdOfBob: String = newCreationSetRequestAndGetTaskId()

    val responseOfAndreRequest = `given`(baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build)
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
           |				"update": { "$taskIdOfBob": { "status": "canceled" } }
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

    assertThatJson(responseOfAndreRequest)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"notUpdated": {
           |			"$taskIdOfBob": {
           |				"type": "notFound",
           |				"description": "Task not found"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def updateShouldReturnNotUpdatedWhenUnknownProperty(): Unit = {
    val taskId: String = newCreationSetRequestAndGetTaskId()

    val response: String = `given`
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
           |				"update": {
           |					"$taskId": {
           |						"status": "canceled",
           |						"redundant": "will get rejected"
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
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"EmailRecoveryAction/set",
           |	{
           |		"notUpdated": {
           |			"$taskId": {
           |				"type": "invalidArguments",
           |				"description": "Unknown properties: redundant"
           |			}
           |		}
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  def newCreationSetRequestAndGetTaskId(): String = {
    `given`
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
           |						"subject": "subject contains"
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
  }

  def templateDeletedMessage(messageId: MessageId = randomMessageId,
                             mailboxId: MailboxId,
                             user: Username = BOB,
                             deliveryDate: ZonedDateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z"),
                             deletionDate: ZonedDateTime = ZonedDateTime.now().minusDays(RESTORATION_HORIZON_SPAN_IN_DAYS - 1),
                             sender: MaybeSender = MaybeSender.of(SENDER),
                             recipients: Seq[MailAddress] = Seq(RECIPIENT1, RECIPIENT2),
                             hasAttachment: Boolean = false,
                             contentLength: Long = DELETED_MESSAGE_CONTENT.length,
                             subject: String = "subject here"): DeletedMessage =
    DeletedMessage.builder
      .messageId(messageId)
      .originMailboxes(mailboxId)
      .user(user)
      .deliveryDate(deliveryDate)
      .deletionDate(deletionDate)
      .sender(sender)
      .recipients(recipients.toList.asJava)
      .hasAttachment(hasAttachment)
      .size(contentLength)
      .subject(subject)
      .build

  def awaitRestoreTaskCompleted(taskId: String): ValidatableResponse =
    `given`()
      .spec(webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("status", Matchers.is("completed"))

  def listAllMessageResult(guiceJamesServer: GuiceJamesServer, username: Username): util.Collection[MessageId] =
    guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
      .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, username.asString(), 100)

  def assertListAllMessageHasSize(guiceJamesServer: GuiceJamesServer, username: Username, size: Int): Unit =
    await().atMost(30, TimeUnit.SECONDS).untilAsserted(() => assertThat(listAllMessageResult(guiceJamesServer, username)).hasSize(size))
}

