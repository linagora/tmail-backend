package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.EncryptHelper.uploadPublicKey
import java.nio.charset.StandardCharsets

import com.linagora.tmail.james.common.probe.JmapGuiceEncryptedEmailContentStoreProbe
import com.linagora.tmail.james.jmap.model.EncryptedEmailGetRequest
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import play.api.libs.json.{JsString, Json}

object LinagoraEncryptedEmailFastViewGetMethodContract {
  val MESSAGE: Message = Message.Builder.of
    .setSubject("test")
    .setSender(BOB.asString)
    .setFrom(BOB.asString)
    .setTo(BOB.asString)
    .setBody("test mail", StandardCharsets.UTF_8)
    .build

  val MESSAGE_PREVIEW: String = "test mail"

  val BOB_INBOX_PATH: MailboxPath = MailboxPath.inbox(BOB)
}

trait LinagoraEncryptedEmailFastViewGetMethodContract {

  import EncryptHelper.decrypt
  import LinagoraEncryptedEmailFastViewGetMethodContract._

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(BOB_INBOX_PATH)

    uploadPublicKey(ACCOUNT_ID, requestSpecification)
  }

  def randomMessageId: MessageId

  @Test
  def methodShouldReturnFailWhenMissingOneCapability(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["1"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState":"${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): com:linagora:params:jmap:pgp"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldReturnFailWhenMissingAllCapabilities(): Unit = {
    val request: String =
      s"""{
         |  "using": [],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["1"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState":"${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:pgp"
         |    },"c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenWrongAccountId(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "unknownAccountId",
         |      "ids": ["${randomMessageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |    "sessionState": "${SESSION_STATE.value}",
         |    "methodResponses": [[
         |            "error",
         |            {
         |                "type": "accountNotFound"
         |            },
         |            "c1"
         |        ]]
         |}""".stripMargin)
  }

  @Test
  def methodShouldFailWhenNumberOfEmailIdsTooLarge(): Unit = {
    val emailIds: Array[String] = LazyList.continually(randomMessageId.serialize()).take(EncryptedEmailGetRequest.MAXIMUM_NUMBER_OF_EMAIL_IDS + 1).toArray
    val emailIdsJson: String = Json.stringify(Json.arr(emailIds)).replace("[[", "[").replace("]]", "]")

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": $emailIdsJson
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].description")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "error",
           |    {
           |          "type": "requestTooLarge",
           |          "description": "The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def methodShouldReturnNotFoundWhenBadEmailId(): Unit = {
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["invalid"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "EncryptedEmailFastView/get",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "state": "${INSTANCE.value}",
           |                "notFound": ["invalid"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def methodShouldReturnNotFoundWhenEmailIdDoesNotExist(): Unit = {
    val messageId: MessageId = randomMessageId
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "EncryptedEmailFastView/get",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "state": "${INSTANCE.value}",
           |                "notFound": [
           |                    "${messageId.serialize}"
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def methodShouldReturnNotFoundWhenEncryptedEmailContentDoesNotExist(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId

    server.getProbe(classOf[JmapGuiceEncryptedEmailContentStoreProbe])
      .delete(messageId)

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "EncryptedEmailFastView/get",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "state": "${INSTANCE.value}",
           |                "notFound": [
           |                    "${messageId.serialize}"
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def methodShouldReturnFastViewWhenEmailIdExits(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
           |    "accountId": "$ACCOUNT_ID",
           |    "state": "${INSTANCE.value}",
           |    "list": [
           |            {
           |                "id": "${messageId.serialize()}",
           |                "encryptedPreview": "$${json-unit.ignore}",
           |                "hasAttachment": false
           |            }
           |    ]
           |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0].encryptedPreview")
      .isNotNull
  }

  @Test
  def encryptedPreviewShouldEncrypt(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId
    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val encryptedPreviewResponse:String = (((Json.parse(response) \\"methodResponses")
      .head \\ "list")
      .head \\ "encryptedPreview")
      .head.asInstanceOf[JsString]
      .value

    assertThat(decrypt(encryptedPreviewResponse))
      .isEqualTo(MESSAGE_PREVIEW)
  }

  @Test
  def encryptedAttachmentsShouldBeDownloadable(server: GuiceJamesServer): Unit = {
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream("emailWithTextAttachment.eml")))
      .getMessageId

    val attachment = `given`
      .basePath("/download")
    .when()
      .get(s"$ACCOUNT_ID/encryptedAttachment_${messageId.serialize()}_0")
    .`then`()
      .extract()
      .body()
      .asString()

    println(attachment)

    assertThat(decrypt(attachment))
      .isEqualTo("This is a beautiful banana.\n")
  }

  @Test
  def methodShouldAcceptSeveralIds(server: GuiceJamesServer): Unit = {
    val messageId1: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId
    val messageId2: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId
    val messageId3: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId1.serialize}", "${messageId2.serialize}", "${messageId3.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .when(Option.IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
           |    "accountId": "$ACCOUNT_ID",
           |    "state": "${INSTANCE.value}",
           |    "list": [
           |            {
           |                "id": "${messageId1.serialize()}",
           |                "encryptedPreview": "$${json-unit.ignore}",
           |                "hasAttachment": false
           |            },
           |            {
           |                "id": "${messageId2.serialize()}",
           |                "encryptedPreview": "$${json-unit.ignore}",
           |                "hasAttachment": false
           |            },
           |            {
           |                "id": "${messageId3.serialize()}",
           |                "encryptedPreview": "$${json-unit.ignore}",
           |                "hasAttachment": false
           |            }
           |        ]
           |}""".stripMargin)
  }

  @Test
  def methodShouldSuccessWhenMixed(server: GuiceJamesServer): Unit = {
    val existMessageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(),
        BOB_INBOX_PATH,
        AppendCommand.from(MESSAGE))
      .getMessageId
    val notFoundMessageId1: MessageId = randomMessageId

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${existMessageId.serialize}", "${notFoundMessageId1.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .when(Option.IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
           |    "accountId": "$ACCOUNT_ID",
           |    "state": "${INSTANCE.value}",
           |    "list": [
           |            {
           |                "id": "${existMessageId.serialize}",
           |                "encryptedPreview": "$${json-unit.ignore}",
           |                "hasAttachment": false
           |            }
           |    ],
           |    "notFound": [
           |        "${notFoundMessageId1.serialize}"
           |    ]
           |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0].encryptedPreview")
      .isNotNull
  }

  @Test
  def methodShouldReturnNotFoundWhenAccountDoesNotHavePermission(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(ANDRE))

    uploadPublicKey(ANDRE_ACCOUNT_ID,
      baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .build)

    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString(),
        MailboxPath.inbox(ANDRE),
        AppendCommand.from(MESSAGE))
      .getMessageId

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "EncryptedEmailFastView/get",
           |            {
           |                "accountId": "$ACCOUNT_ID",
           |                "state": "${INSTANCE.value}",
           |                "notFound": [
           |                    "${messageId.serialize}"
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def methodShouldReturnNotFoundWhenAccountDoesNotHaveAnyKeyStore(server: GuiceJamesServer) : Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
    val mailboxProbe: MailboxProbeImpl = server.getProbe(classOf[MailboxProbeImpl])
    mailboxProbe.createMailbox(MailboxPath.inbox(ANDRE))

    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(ANDRE.asString(),
        MailboxPath.inbox(ANDRE),
        AppendCommand.from(MESSAGE))
      .getMessageId

    val request: String =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:pgp"],
         |  "methodCalls": [
         |    ["EncryptedEmailFastView/get", {
         |      "accountId": "$ANDRE_ACCOUNT_ID",
         |      "ids": ["${messageId.serialize}"]
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response: String =
      `given`(baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .build)
        .body(request)
      .when()
        .post()
      .`then`
        .statusCode(HttpStatus.SC_OK)
        .contentType(JSON)
        .extract()
        .body()
        .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "EncryptedEmailFastView/get",
           |            {
           |                "accountId": "$ANDRE_ACCOUNT_ID",
           |                "state": "${INSTANCE.value}",
           |                "notFound": [
           |                    "${messageId.serialize}"
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

}
