package com.linagora.tmail.james.common

import java.io.ByteArrayInputStream
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.EmailRecoveryActionGetMethodContract.webAdminApi
import com.linagora.tmail.james.common.EmailRecoveryActionSetMethodContract.DELETED_MESSAGE_CONTENT
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
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.model.{MailboxId, MailboxPath, MessageId, MultimailboxesSearchQuery, SearchQuery}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.{DataProbeImpl, WebAdminGuiceProbe}
import org.apache.james.vault.DeletedMessage
import org.apache.james.webadmin.WebAdminUtils
import org.apache.mailet.base.MailAddressFixture.{RECIPIENT1, RECIPIENT2, SENDER}
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Test}

import scala.jdk.CollectionConverters._

object EmailRecoveryActionGetMethodContract {
  private var webAdminApi: RequestSpecification = _
}

trait EmailRecoveryActionGetMethodContract {
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
      .setBasePath("/tasks")
      .build()
  }

  def randomMessageId: MessageId

  @Test
  def shouldFailWhenOmittingVaultCapability(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["2034-495-05857-57abcd-0876664"]
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
  def shouldFailWhenNullIds(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |         "ids": null
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
           |	"error",
           |	{
           |		"type": "invalidArguments",
           |		"description": "'/ids' property need to be an array"
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def shouldFailWhenOmittingIds(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
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
           |	"error",
           |	{
           |		"type": "invalidArguments",
           |		"description": "Missing '/ids' property"
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def emptyIdsCaseShouldReturnEmpty(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |         "ids": []
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
           |	"EmailRecoveryAction/get",
           |	{
           |		"notFound": [],
           |		"list": []
           |	},
           |	"c1"
           |]""".stripMargin)
  }

  @Test
  def shouldReturnAllPropertiesByDefault(server: GuiceJamesServer): Unit = {
    val bobMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    val deletedMessage: DeletedMessage = templateDeletedMessage(
      mailboxId = bobMailboxId,
      subject = "subject contains should match")
    server.getProbe(classOf[DeletedMessageVaultProbe])
      .append(deletedMessage, new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))
    awaitAllMessagesCount(server, BOB, 0)

    val taskId: String = createMessagesRestoreTask(subjectQuery = "subject contains")
    awaitTaskCompletion(taskId)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["$taskId"]
           |			},
           |			"#0"
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
           |	"EmailRecoveryAction/get",
           |	{
           |		"notFound": [],
           |		"list": [{
           |			"id": "$taskId",
           |			"successfulRestoreCount": 1,
           |			"errorRestoreCount": 0,
           |			"status": "completed"
           |		}]
           |	},
           |	"#0"
           |]""".stripMargin)

    awaitAllMessagesCount(server, BOB, 1)
  }

  @Test
  def getNonExistingTaskShouldReturnNotFound(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["77731634-ea82-4a1a-bd4c-9f8ece4f66c7"]
           |			},
           |			"#0"
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
        s"""["EmailRecoveryAction/get",
           |	{
           |		"notFound": ["77731634-ea82-4a1a-bd4c-9f8ece4f66c7"],
           |		"list": []
           |	},
           |	"#0"
           |]""".stripMargin)
  }

  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val bobMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    val deletedMessage: DeletedMessage = templateDeletedMessage(
      mailboxId = bobMailboxId,
      subject = "subject contains should match")
    server.getProbe(classOf[DeletedMessageVaultProbe])
      .append(deletedMessage, new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))
    awaitAllMessagesCount(server, BOB, 0)

    val taskId: String = createMessagesRestoreTask(subjectQuery = "subject contains")
    awaitTaskCompletion(taskId)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["$taskId", "notFoundTaskId"]
           |			},
           |			"#0"
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
           |	"EmailRecoveryAction/get",
           |	{
           |		"notFound": ["notFoundTaskId"],
           |		"list": [{
           |			"id": "$taskId",
           |			"successfulRestoreCount": 1,
           |			"errorRestoreCount": 0,
           |			"status": "completed"
           |		}]
           |	},
           |	"#0"
           |]""".stripMargin)

    awaitAllMessagesCount(server, BOB, 1)
  }

  @Test
  def shouldFilterProperties(server: GuiceJamesServer): Unit = {
    val bobMailboxId: MailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))

    val deletedMessage: DeletedMessage = templateDeletedMessage(
      mailboxId = bobMailboxId,
      subject = "subject contains should match")
    server.getProbe(classOf[DeletedMessageVaultProbe])
      .append(deletedMessage, new ByteArrayInputStream(DELETED_MESSAGE_CONTENT))
    awaitAllMessagesCount(server, BOB, 0)

    val taskId: String = createMessagesRestoreTask(subjectQuery = "subject contains")
    awaitTaskCompletion(taskId)

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["$taskId"],
           |        "properties": ["status"]
           |			},
           |			"#0"
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
           |	"EmailRecoveryAction/get",
           |	{
           |		"notFound": [],
           |		"list": [{
           |			"id": "$taskId",
           |			"status": "completed"
           |		}]
           |	},
           |	"#0"
           |]""".stripMargin)

    awaitAllMessagesCount(server, BOB, 1)
  }

  @Test
  def shouldFailWhenInvalidProperties(): Unit = {
    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["aTaskId"],
           |        "properties": ["invalid"]
           |			},
           |			"#0"
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
           |	"error",
           |	{
           |		"type": "invalidArguments",
           |		"description": "The following properties [invalid] are not supported."
           |	},
           |	"#0"
           |]""".stripMargin)
  }

  @Test
  def shouldNotReturnNonDeletedMessagesVaultRestoreTask(): Unit = {
    val otherTaskTypeId: String = `given`()
      .spec(webAdminApi)
      .basePath("/mailboxes")
      .queryParam("task", "populateEmailQueryView")
      .post
      .jsonPath
      .get("taskId")

    val response = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["$otherTaskTypeId"],
           |        "properties": ["status"]
           |			},
           |			"#0"
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
           |	"EmailRecoveryAction/get",
           |	{
           |		"notFound": ["$otherTaskTypeId"],
           |		"list": []
           |	},
           |	"#0"
           |]""".stripMargin)
  }

  @Test
  def shouldNotReturnDeletedMessagesVaultRestoreTaskOfOtherUsers(): Unit = {
    val andreTaskId: String = createMessagesRestoreTask(userCredential = UserCredential(ANDRE, ANDRE_PASSWORD),
      subjectQuery = "subject contains")

    val response = `given`
      .auth.basic(BOB.asString(), BOB_PASSWORD)
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:messages:vault"],
           |	"methodCalls": [
           |		["EmailRecoveryAction/get",
           |			{
           |				"ids": ["$andreTaskId"],
           |        "properties": ["status"]
           |			},
           |			"#0"
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
           |	"EmailRecoveryAction/get",
           |	{
           |		"notFound": ["$andreTaskId"],
           |		"list": []
           |	},
           |	"#0"
           |]""".stripMargin)
  }

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

  def templateDeletedMessage(messageId: MessageId = randomMessageId,
                             mailboxId: MailboxId,
                             user: Username = BOB,
                             deliveryDate: ZonedDateTime = ZonedDateTime.parse("2014-10-30T14:12:00Z"),
                             deletionDate: ZonedDateTime = ZonedDateTime.parse("2015-10-30T14:12:00Z"),
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

  private def awaitAllMessagesCount(guiceJamesServer: GuiceJamesServer, username: Username, count: Int): Unit = {
    def listAllMessageResult(guiceJamesServer: GuiceJamesServer, username: Username): java.util.Collection[MessageId] =
      guiceJamesServer.getProbe(classOf[MailboxProbeImpl])
        .searchMessage(MultimailboxesSearchQuery.from(SearchQuery.of(SearchQuery.all())).build, username.asString(), 100)

    await().atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() => assertThat(listAllMessageResult(guiceJamesServer, username))
      .hasSize(count))
  }

  private def awaitTaskCompletion(taskId: String): ValidatableResponse =
    `given`()
      .spec(webAdminApi)
      .get(taskId + "/await")
    .`then`()
      .body("status", Matchers.is("completed"))
}
