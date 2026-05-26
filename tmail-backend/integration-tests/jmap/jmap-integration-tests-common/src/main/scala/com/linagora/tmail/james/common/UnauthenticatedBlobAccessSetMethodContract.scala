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
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.blob.{UnauthenticatedBlobDownloadToken, UnauthenticatedBlobDownloadTokenRepository}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.blob.api.BlobId
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.util.ClassLoaderUtils
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import play.api.libs.json.{JsObject, Json}

object UnauthenticatedBlobAccessSetMethodContract {
  private val CAPABILITY: String = "com:linagora:params:jmap:unauthenticated:blob:access"

  case class TestContext(bobUsername: Username,
                         bobAccountId: String,
                         andreUsername: Username,
                         andreAccountId: String)

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()
}

class UnauthenticatedBlobAccessTokenRepositoryProbe @Inject()(repository: UnauthenticatedBlobDownloadTokenRepository,
                                                              blobIdFactory: BlobId.Factory) extends GuiceProbe {
  def isValid(accountId: String, blobId: String, token: String): Boolean =
    repository.check(JavaAccountId.fromString(accountId), blobIdFactory.parse(blobId), new UnauthenticatedBlobDownloadToken(UUID.fromString(token)))
      .block()
      .booleanValue()
}

class UnauthenticatedBlobAccessTokenRepositoryProbeModule extends AbstractModule {
  override def configure(): Unit =
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[UnauthenticatedBlobAccessTokenRepositoryProbe])
}

trait UnauthenticatedBlobAccessSetMethodContract {
  import UnauthenticatedBlobAccessSetMethodContract._

  def bobUsername: Username = currentContext.get().bobUsername
  def bobAccountId: String = currentContext.get().bobAccountId
  def andreUsername: Username = currentContext.get().andreUsername
  def andreAccountId: String = currentContext.get().andreAccountId

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = sha256AccountId(bob),
      andreUsername = andre,
      andreAccountId = sha256AccountId(andre)))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(bob.asString, BOB_PASSWORD)
      .addUser(andre.asString, ANDRE_PASSWORD)

    requestSpecification = authenticatedSpec(server, bob, BOB_PASSWORD)
  }

  @Test
  def sessionShouldAdvertiseCapability(): Unit =
    assertThatJson(`given`()
      .when()
        .get("/session")
      .`then`()
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body()
        .asString())
      .inPath(s"capabilities['$CAPABILITY']")
      .isEqualTo(
        s"""{
           |    "endpoint": "http://localhost/unauthenticatedDownload/{accountId}/{blobId}?token={token}",
           |    "tokenTtlInSeconds": 300
           |}""".stripMargin)

  @Test
  def missingCapabilityShouldReturnUnknownMethod(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadBlob(server, bobUsername, BOB_PASSWORD, bobAccountId)

    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "$blobId": {}
         |      }
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "unknownMethod",
           |        "description": "Missing capability(ies): $CAPABILITY"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def createShouldReturnTokenForAccessibleBlob(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadBlob(server, bobUsername, BOB_PASSWORD, bobAccountId)
    val response: String = createAccess(server, bobAccountId, blobId)
    val token: String = createdToken(response, blobId)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "UnauthenticatedBlobAccess/set",
           |    {
           |        "accountId": "$bobAccountId",
           |        "created": {
           |            "$blobId": {
           |                "token": "$${json-unit.ignore}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
    assertThat(tokenProbe(server).isValid(bobAccountId, blobId, token)).isTrue
  }

  @Test
  def shouldGrantAccessForMessageBlob(server: GuiceJamesServer): Unit = {
    val messageBlobId = appendMessage(server, bobUsername).serialize()
    val response: String = createAccess(server, bobAccountId, messageBlobId)
    val token: String = createdToken(response, messageBlobId)

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isEqualTo(
        s"""{
           |    "$messageBlobId": {
           |        "token": "$${json-unit.ignore}"
           |    }
           |}""".stripMargin)
    assertThat(tokenProbe(server).isValid(bobAccountId, messageBlobId, token)).isTrue
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldGrantAccessForAttachmentBlob(server: GuiceJamesServer): Unit = {
    val messageId = appendMessageFromResource(server, bobUsername, "emailWithTextAttachment.eml")
    val attachmentBlobId: String = firstAttachmentBlobId(server, bobUsername, BOB_PASSWORD, bobAccountId, messageId)
    val response: String = createAccess(server, bobAccountId, attachmentBlobId)
    val token: String = createdToken(response, attachmentBlobId)

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isEqualTo(
        s"""{
           |    "$attachmentBlobId": {
           |        "token": "$${json-unit.ignore}"
           |    }
           |}""".stripMargin)
    assertThat(tokenProbe(server).isValid(bobAccountId, attachmentBlobId, token)).isTrue
  }

  @Test
  def invalidBlobIdShouldReturnNotCreatedInvalidArguments(server: GuiceJamesServer): Unit = {
    val response: String = createAccess(server, bobAccountId, "")

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "UnauthenticatedBlobAccess/set",
           |    {
           |        "accountId": "$bobAccountId",
           |        "notCreated": {
           |            "": {
           |                "type": "invalidArguments",
           |                "description": "$${json-unit.ignore}"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def invalidCreateObjectShouldReturnNotCreatedWithInvalidArguments(server: GuiceJamesServer): Unit = {
    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "blobId": {
         |          "unexpected": true
         |        }
         |      }
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "blobId": {
           |        "type": "invalidArguments",
           |        "description": "create value must be an empty object"
           |    }
           |}""".stripMargin)
  }

  @Test
  def nonObjectCreateValueShouldReturnNotCreatedInvalidArguments(server: GuiceJamesServer): Unit = {
    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "blobId": true
         |      }
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "blobId": {
           |        "type": "invalidArguments",
           |        "description": "create value must be an empty object"
           |    }
           |}""".stripMargin)
  }

  @Test
  def nonExistingBlobShouldReturnNotCreatedNotFound(server: GuiceJamesServer): Unit = {
    val response: String = createAccess(server, bobAccountId, "nonexistentBlobId")

    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "nonexistentBlobId": {
           |        "type": "notFound",
           |        "description": "Blob BlobId(nonexistentBlobId) could not be found"
           |    }
           |}""".stripMargin)
  }

  @Test
  def foreignUploadedBlobShouldReturnNotCreatedNotFound(server: GuiceJamesServer): Unit = {
    val andreBlobId: String = uploadBlob(server, andreUsername, ANDRE_PASSWORD, andreAccountId)
    val response: String = createAccess(server, bobAccountId, andreBlobId)

    // Bob should not be granted access to Andre's blob
    assertThatJson(response)
      .inPath("methodResponses[0][1].notCreated")
      .isEqualTo(
        s"""{
           |    "$andreBlobId": {
           |        "type": "notFound",
           |        "description": "Blob BlobId($andreBlobId) could not be found"
           |    }
           |}""".stripMargin)
  }

  @Test
  def delegatedAccountShouldReturnTokenBoundToDelegatedAccount(server: GuiceJamesServer): Unit = {
    val andreBlobId = appendMessage(server, andreUsername).serialize()
    // GIVEN Andre delegates Bob to access Andre account
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(andreUsername, bobUsername)

    // WHEN Bob grant access to Andre's blob using Andre's accountId
    val response: String = createAccess(server, andreAccountId, andreBlobId,
      username = bobUsername, password = BOB_PASSWORD)
    val token: String = createdToken(response, andreBlobId)

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isEqualTo(
        s"""{
           |    "$andreBlobId": {
           |        "token": "$${json-unit.ignore}"
           |    }
           |}""".stripMargin)

    // THEN the blob should be granted under Andre accountId, not bob accountId
    assertThat(tokenProbe(server).isValid(andreAccountId, andreBlobId, token)).isTrue
    assertThat(tokenProbe(server).isValid(bobAccountId, andreBlobId, token)).isFalse
  }

  @Test
  def delegatedAccountWithoutDelegationShouldReturnAccountNotFound(server: GuiceJamesServer): Unit = {
    val andreBlobId = appendMessage(server, andreUsername).serialize()

    val response: String = createAccess(server, andreAccountId, andreBlobId,
      username = bobUsername, password = BOB_PASSWORD)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "accountNotFound"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def mixedCreateShouldReturnCreatedAndNotCreated(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadBlob(server, bobUsername, BOB_PASSWORD, bobAccountId)
    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "$blobId": {},
         |        "nonexistentBlobId": {}
         |      }
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "UnauthenticatedBlobAccess/set",
           |    {
           |        "accountId": "$bobAccountId",
           |        "created": {
           |            "$blobId": {
           |                "token": "$${json-unit.ignore}"
           |            }
           |        },
           |        "notCreated": {
           |            "nonexistentBlobId": {
           |                "type": "notFound",
           |                "description": "Blob BlobId(nonexistentBlobId) could not be found"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def secondTokenShouldInvalidateFirstToken(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadBlob(server, bobUsername, BOB_PASSWORD, bobAccountId)
    val firstToken: String = createdToken(createAccess(server, bobAccountId, blobId), blobId)
    val response: String = createAccess(server, bobAccountId, blobId)
    val secondToken: String = createdToken(response, blobId)

    assertThatJson(response)
      .inPath("methodResponses[0][1].created")
      .isEqualTo(
        s"""{
           |    "$blobId": {
           |        "token": "$${json-unit.ignore}"
           |    }
           |}""".stripMargin)
    assertThat(firstToken).isNotEqualTo(secondToken)
    assertThat(tokenProbe(server).isValid(bobAccountId, blobId, firstToken)).isFalse
    assertThat(tokenProbe(server).isValid(bobAccountId, blobId, secondToken)).isTrue
  }

  @Test
  def tooManyCreateEntriesShouldReturnRequestTooLarge(server: GuiceJamesServer): Unit = {
    val createEntries: String = (1 to 600)
      .map(index => s""""blob$index": {}""")
      .mkString(",")

    val response = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        $createEntries
         |      }
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "error",
           |    {
           |        "type": "requestTooLarge",
           |        "description": "$${json-unit.ignore}"
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def updateShouldNotBeSupported(server: GuiceJamesServer): Unit = {
    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "update": {
         |        "blobId": {}
         |      }
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "UnauthenticatedBlobAccess/set",
           |    {
           |        "accountId": "$bobAccountId",
           |        "notUpdated": {
           |            "blobId": {
           |                "type": "invalidArguments",
           |                "description": "`update` is not supported by UnauthenticatedBlobAccess/set"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def destroyShouldNotBeSupported(server: GuiceJamesServer): Unit = {
    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "destroy": ["blobId"]
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)

    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "UnauthenticatedBlobAccess/set",
           |    {
           |        "accountId": "$bobAccountId",
           |        "notDestroyed": {
           |            "blobId": {
           |                "type": "invalidArguments",
           |                "description": "`destroy` is not supported by UnauthenticatedBlobAccess/set"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  @Test
  def createWithUpdateAndDestroyShouldReturnAllResultSections(server: GuiceJamesServer): Unit = {
    val blobId: String = uploadBlob(server, bobUsername, BOB_PASSWORD, bobAccountId)
    val response: String = postJmap(server, bobUsername, BOB_PASSWORD,
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
         |  "methodCalls": [[
         |    "UnauthenticatedBlobAccess/set",
         |    {
         |      "accountId": "$bobAccountId",
         |      "create": {
         |        "$blobId": {}
         |      },
         |      "update": {
         |        "updatedBlob": {}
         |      },
         |      "destroy": ["destroyedBlob"]
         |    },
         |    "c1"
         |  ]]
         |}""".stripMargin)
    assertThatJson(response)
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |    "UnauthenticatedBlobAccess/set",
           |    {
           |        "accountId": "$bobAccountId",
           |        "created": {
           |            "$blobId": {
           |                "token": "$${json-unit.ignore}"
           |            }
           |        },
           |        "notUpdated": {
           |            "updatedBlob": {
           |                "type": "invalidArguments",
           |                "description": "`update` is not supported by UnauthenticatedBlobAccess/set"
           |            }
           |        },
           |        "notDestroyed": {
           |            "destroyedBlob": {
           |                "type": "invalidArguments",
           |                "description": "`destroy` is not supported by UnauthenticatedBlobAccess/set"
           |            }
           |        }
           |    },
           |    "c1"
           |]""".stripMargin)
  }

  private def sha256AccountId(username: Username): String =
    Hashing.sha256()
      .hashString(username.asString(), StandardCharsets.UTF_8)
      .toString

  private def authenticatedSpec(server: GuiceJamesServer, username: Username, password: String): RequestSpecification =
    baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(username, password)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
      .log()
      .ifValidationFails()

  private def uploadBlob(server: GuiceJamesServer,
                         username: Username,
                         password: String,
                         accountId: String,
                         contentType: String = "text/plain"): String =
    `given`(authenticatedSpec(server, username, password))
      .basePath("")
      .contentType(contentType)
      .body(UUID.randomUUID().toString)
    .when()
      .post(s"/upload/$accountId")
    .`then`()
      .statusCode(SC_CREATED)
      .extract()
      .path("blobId")

  private def appendMessage(server: GuiceJamesServer, username: Username): MessageId = {
    val path = MailboxPath.inbox(username)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(username.asString, path, AppendCommand.from(Message.Builder.of()
        .setSubject("subject")
        .setBody("body", StandardCharsets.UTF_8)
        .build()))
      .getMessageId
  }

  private def appendMessageFromResource(server: GuiceJamesServer, username: Username, resourceName: String): MessageId = {
    val path = MailboxPath.inbox(username)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(path)
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(username.asString, path, AppendCommand.from(ClassLoaderUtils.getSystemResourceAsSharedStream(resourceName)))
      .getMessageId
  }

  private def firstAttachmentBlobId(server: GuiceJamesServer,
                                    username: Username,
                                    password: String,
                                    accountId: String,
                                    messageId: MessageId): String =
    `given`(authenticatedSpec(server, username, password))
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
           |  "methodCalls": [[
           |    "Email/get",
           |    {
           |      "accountId": "$accountId",
           |      "ids": ["${messageId.serialize()}"],
           |      "properties": ["attachments"],
           |      "bodyProperties": ["blobId"]
           |    },
           |    "c1"
           |  ]]
           |}""".stripMargin)
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .path("methodResponses[0][1].list[0].attachments[0].blobId")

  private def createAccess(server: GuiceJamesServer,
                           accountId: String,
                           blobId: String,
                           username: Username = bobUsername,
                           password: String = BOB_PASSWORD): String =
    postJmap(server, username, password, createRequest(accountId, blobId))

  private def createRequest(accountId: String, blobId: String): String =
    s"""{
       |  "using": ["urn:ietf:params:jmap:core", "$CAPABILITY"],
       |  "methodCalls": [[
       |    "UnauthenticatedBlobAccess/set",
       |    {
       |      "accountId": "$accountId",
       |      "create": {
       |        "$blobId": {}
       |      }
       |    },
       |    "c1"
       |  ]]
       |}""".stripMargin

  private def postJmap(server: GuiceJamesServer, username: Username, password: String, request: String): String =
    `given`(authenticatedSpec(server, username, password))
      .body(request)
    .when()
      .post()
    .`then`()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

  private def firstArguments(response: String): JsObject =
    (Json.parse(response) \ "methodResponses" \ 0 \ 1).as[JsObject]

  private def createdToken(response: String, blobId: String): String =
    (firstArguments(response) \ "created" \ blobId \ "token").as[String]

  private def tokenProbe(server: GuiceJamesServer): UnauthenticatedBlobAccessTokenRepositoryProbe =
    server.getProbe(classOf[UnauthenticatedBlobAccessTokenRepositoryProbe])
}
