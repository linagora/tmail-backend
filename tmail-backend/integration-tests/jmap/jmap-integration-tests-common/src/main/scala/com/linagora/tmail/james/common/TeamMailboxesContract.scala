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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.TeamMailboxesContract.webAdminApi
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxProbe}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import io.restassured.specification.RequestSpecification
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.{SC_CREATED, SC_OK}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.core.quota.{QuotaCountLimit, QuotaSizeLimit}
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.{PushState, UTCDate, UuidState}
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DownloadContract.accountId
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.receiveMessageInTimespan
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.mailbox.DefaultMailboxes
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath, MessageId}
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageWriter
import org.apache.james.mime4j.stream.RawField
import org.apache.james.modules.{MailboxProbeImpl, QuotaProbesImpl}
import org.apache.james.utils.{DataProbeImpl, WebAdminGuiceProbe}
import org.apache.james.webadmin.WebAdminUtils
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.hamcrest.Matchers
import org.hamcrest.Matchers.{containsString, equalTo, hasKey, hasSize}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import play.api.libs.json.{JsArray, Json}
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.ws.WebSocketFrame

import scala.concurrent.duration.MILLISECONDS

object TeamMailboxesContract {
  private var webAdminApi: RequestSpecification = _
}

trait TeamMailboxesContract {
  private lazy val BOB_ACCOUNT_ID: String = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
  private lazy val UTC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)
  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(CEDRIC.asString(), "CEDRIC_pass")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build

    webAdminApi = WebAdminUtils.buildRequestSpecification(server.getProbe(classOf[WebAdminGuiceProbe]).getWebAdminPort)
      .build()
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def givenTeamMailboxHasManyMembersThenIdentitySetShouldSucceed(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, CEDRIC)
      .addMember(teamMailbox, BOB)

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:submission"
         |	],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"create": {
         |					"4f29": {
         |						"name": "test",
         |						"email": "hiring@domain.tld",
         |						"textSignature": "Some text signature",
         |						"htmlSignature": "<p>Some html signature</p>"
         |					}
         |				}
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"newState": "${SESSION_STATE.value}",
           |				"created": {
           |					"4f29": {
           |						"id": "$${json-unit.ignore}",
           |						"mayDelete": true
           |					}
           |				}
           |			},
           |			"c0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def givenBobIsNotBelongToTeamMailboxThenIdentitySetShouldFail(): Unit = {
    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:submission"
         |	],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$BOB_ACCOUNT_ID",
         |				"create": {
         |					"4f29": {
         |						"name": "test",
         |						"email": "hiring@domain.tld"
         |					}
         |				}
         |			},
         |			"c0"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"newState": "${SESSION_STATE.value}",
           |				"notCreated": {
           |					"4f29": {
           |						"type": "forbiddenFrom",
           |						"description": "Can not send from hiring@domain.tld"
           |					}
           |				}
           |			},
           |			"c0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def identityGetShouldListTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val request =s"""{
                    |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
                    |  "methodCalls": [[
                    |    "Identity/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": null
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "list": [
           |                    {
           |                        "id": "$${json-unit.ignore}",
           |                        "name": "bob@domain.tld",
           |                        "email": "bob@domain.tld",
           |                        "htmlSignature": "",
           |                        "textSignature": "",
           |                        "mayDelete": false
           |                    },
           |                    {
           |                        "id": "$${json-unit.ignore}",
           |                        "name": "marketing@domain.tld",
           |                        "email": "marketing@domain.tld",
           |                        "htmlSignature": "",
           |                        "textSignature": "",
           |                        "mayDelete": false
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def EmailSubmissionSetGetShouldAcceptTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(s"marketing@${DOMAIN.asString()}")
      .setFrom(s"marketing@${DOMAIN.asString()}")
      .setTo(BOB.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request =s"""{
                    |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
                    |  "methodCalls": [
                    |     ["EmailSubmission/set", {
                    |       "accountId": "$ACCOUNT_ID",
                    |       "create": {
                    |         "k1490": {
                    |           "emailId": "${messageId.serialize}",
                    |           "envelope": {
                    |             "mailFrom": {"email": "marketing@${DOMAIN.asString()}"},
                    |             "rcptTo": [{"email": "${BOB.asString}"}]
                    |           }
                    |         }
                    |    }
                    |  }, "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "EmailSubmission/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "created": {
           |                    "k1490": "$${json-unit.ignore}"
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldListBaseMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "name": "marketing",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldListInboxMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.inboxPath)

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "parentId": "$id2",
           |          "name": "INBOX",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldListSentMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.sentPath)

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "parentId": "$id2",
           |          "name": "Sent",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldListOutboxMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath("Outbox"))

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "parentId": "$id2",
           |          "name": "Outbox",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldListDraftsMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath("Drafts"))

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "parentId": "$id2",
           |          "name": "Drafts",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldListTrashMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath("Trash"))

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "parentId": "$id2",
           |          "name": "Trash",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldNotReturnTeamMailboxesWhenNoShareExtension(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.sentPath)

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val id3 = mailboxId(server, teamMailbox.inboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1", "$id2", "$id3"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [],
           |      "notFound": ["$id1", "$id2", "$id3"]
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def mailboxGetShouldNotReturnTeamMailboxesWhenNotAMember(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)

    val id1 = mailboxId(server, teamMailbox.sentPath)

    val id2 = mailboxId(server, teamMailbox.mailboxPath)

    val id3 = mailboxId(server, teamMailbox.inboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1", "$id2", "$id3"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [],
           |      "notFound": ["$id1", "$id2", "$id3"]
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  private def mailboxId(server: GuiceJamesServer, path: MailboxPath) = {
    server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(path.getNamespace, path.getUser.asString(), path.getName)
      .serialize()
  }

  @Test
  def mailboxGetShouldNotListRightsOfOthers(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
      .addMember(teamMailbox, CEDRIC)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val request =s"""{
                    |  "using": [
                    |    "urn:ietf:params:jmap:core",
                    |    "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id1"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [[
           |    "Mailbox/get",
           |    {
           |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "list": [
           |        {
           |          "id": "$id1",
           |          "name": "marketing",
           |          "sortOrder": 1000,
           |          "totalEmails": 0,
           |          "unreadEmails": 0,
           |          "totalThreads": 0,
           |          "unreadThreads": 0,
           |          "myRights": {
           |            "mayReadItems": true,
           |            "mayAddItems": true,
           |            "mayRemoveItems": true,
           |            "maySetSeen": true,
           |            "maySetKeywords": true,
           |            "mayCreateChild": false,
           |            "mayRename": false,
           |            "mayDelete": false,
           |            "maySubmit": false
           |          },
           |          "isSubscribed": true,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["e", "i", "l", "p", "r", "s", "t", "w"]}
           |        }
           |      ],
           |      "notFound": []
           |    },
           |    "c1"]]
           |}""".stripMargin)
  }

  @Test
  def renamingATeamMailboxShouldFail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "$id1": {
         |                      "name": "otherName"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |  "methodResponses":[[
           |    "Mailbox/set",
           |    {
           |      "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "notUpdated":{
           |        "$id1":{
           |          "type":"forbidden",
           |          "description":"Invalid change to a delegated mailbox"
           |        }
           |      }
           |    },
           |    "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def deletingATeamMailboxShouldFail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.inboxPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "destroy": ["$id1"]
         |           },
         |    "c1"
         |       ]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Mailbox/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notDestroyed": {
           |                    "$id1": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.INBOX'"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def creatingATeamMailboxChildShouldSuccess(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.inboxPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "create": {
         |                  "K39" : {
         |                    "name": "aname",
         |                    "parentId": "$id1"
         |                  }
         |                }
         |           },
         |    "c1"
         |       ]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |  "sessionState": "$${json-unit.ignore}",
           |  "methodResponses": [
           |    [
           |      "Mailbox/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "oldState": "f318cfb6-5413-4d72-a007-f24652c1ca30",
           |        "newState": "974e75a7-0e24-4490-8eea-e583c77ddec8",
           |        "created": {
           |          "K39": {
           |            "id": "$${json-unit.ignore}",
           |            "sortOrder": 1000,
           |            "totalEmails": 0,
           |            "unreadEmails": 0,
           |            "totalThreads": 0,
           |            "unreadThreads": 0,
           |            "myRights": {
           |              "mayReadItems": true,
           |              "mayAddItems": true,
           |              "mayRemoveItems": true,
           |              "maySetSeen": true,
           |              "maySetKeywords": true,
           |              "mayCreateChild": true,
           |              "mayRename": true,
           |              "mayDelete": true,
           |              "maySubmit": true
           |            },
           |            "isSubscribed": true
           |          }
           |        }
           |      },
           |      "c1"
           |    ]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def creatingATopChildMailboxOfTeamMailboxShouldSuccess(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
    val marketingTeamMailboxId = mailboxId(server, teamMailbox.mailboxPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "create": {
         |                  "K39" : {
         |                    "name": "ChildOfTopMailbox",
         |                    "parentId": "$marketingTeamMailboxId"
         |                  }
         |                }
         |           },
         |       "c1"]
         |    ]
         |}""".stripMargin

     val childMailboxId: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .path("methodResponses[0][1].created.K39.id")

    awaitAtMostTenSeconds.untilAsserted(()=> {
      val getResponse = `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(s"""{
                 |    "using": [
                 |      "urn:ietf:params:jmap:core",
                 |      "urn:ietf:params:jmap:mail",
                 |      "urn:apache:james:params:jmap:mail:shares"],
                 |    "methodCalls": [
                 |             ["Mailbox/get",
                 |           {
                 |             "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                 |             "ids": ["$childMailboxId"]
                 |            },
                 |         "c2"]
                 |
                 |      ]
                 |  }""".stripMargin)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .extract()
        .body()
        .asString()

      assertThatJson(getResponse)
        .isEqualTo(
          s"""
             |{
             |    "sessionState": "$${json-unit.ignore}",
             |    "methodResponses": [
             |      [
             |        "Mailbox/get",
             |        {
             |          "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |          "state": "$${json-unit.ignore}",
             |          "list": [
             |            {
             |              "id": "$childMailboxId",
             |              "name": "ChildOfTopMailbox",
             |              "parentId": "$marketingTeamMailboxId",
             |              "sortOrder": 1000,
             |              "totalEmails": 0,
             |              "unreadEmails": 0,
             |              "totalThreads": 0,
             |              "unreadThreads": 0,
             |              "myRights": {
             |                "mayReadItems": true,
             |                "mayAddItems": true,
             |                "mayRemoveItems": true,
             |                "maySetSeen": true,
             |                "maySetKeywords": true,
             |                "mayCreateChild": false,
             |                "mayRename": true,
             |                "mayDelete": true,
             |                "maySubmit": false
             |              },
             |              "isSubscribed": true,
             |              "namespace": "TeamMailbox[marketing@domain.tld]",
             |              "rights": {
             |                "bob@domain.tld": [ "e", "i", "l", "p", "r", "s", "t", "w", "x" ]
             |              }
             |            }
             |          ],
             |          "notFound": []
             |        },
             |        "c2"
             |      ]
             |    ]
             |  }""".stripMargin)
    })
  }

  @Test
  def deleteCustomMailboxShouldSuccess(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
    val marketingTeamMailboxId = mailboxId(server, teamMailbox.mailboxPath)

    val mailboxId1: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "create": {
               |                  "K39" : {
               |                    "name": "ChildOfTopMailbox",
               |                    "parentId": "$marketingTeamMailboxId"
               |                  }
               |                }
               |           },
               |       "c1"]
               |    ]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .extract()
      .path("methodResponses[0][1].created.K39.id")

    // Verify that bob has the right to delete the mailbox
    awaitAtMostTenSeconds.untilAsserted(()=> {
      `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(s"""{
                 |    "using": [
                 |      "urn:ietf:params:jmap:core",
                 |      "urn:ietf:params:jmap:mail",
                 |      "urn:apache:james:params:jmap:mail:shares"],
                 |    "methodCalls": [
                 |             ["Mailbox/get",
                 |           {
                 |             "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                 |             "ids": ["$mailboxId1"]
                 |            },
                 |         "c2"]
                 |
                 |      ]
                 |  }""".stripMargin)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .body("methodResponses[0][1].list[0].myRights.mayDelete", equalTo(true))
    })

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "destroy": ["$mailboxId1"]
               |           },
               |    "c1"
               |       ]]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].destroyed", hasSize(1))
      .body("methodResponses[0][1].destroyed[0]", equalTo(mailboxId1))
  }

  @Test
  def deleteCustomMailboxShouldFailWhenNotAMember(server: GuiceJamesServer): Unit = {
    // Given a team mailbox & bob as a member
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val marketingTeamMailboxId = mailboxId(server, teamMailbox.mailboxPath)

    val mailboxId1: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "create": {
               |                  "K39" : {
               |                    "name": "ChildOfTopMailbox",
               |                    "parentId": "$marketingTeamMailboxId"
               |                  }
               |                }
               |           },
               |       "c1"],
               |           ["Mailbox/get",
               |         {
               |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |           "ids": ["#K39"]
               |          },
               |       "c2"]
               |
               |    ]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .extract()
      .path("methodResponses[0][1].created.K39.id")

    Thread.sleep(500)
    // Removing bob from the team mailbox
    server.getProbe(classOf[TeamMailboxProbe])
      .removeMember(teamMailbox, BOB)

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "urn:ietf:params:jmap:mail",
                |    "urn:apache:james:params:jmap:mail:shares"],
                |  "methodCalls": [[
                |           "Mailbox/set",
                |           {
                |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                |                "destroy": ["$mailboxId1"]
                |           },
                |    "c1"
                |       ]]
                |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notDestroyed." + mailboxId1 + ".type", Matchers.is("notFound"))
  }

  @Test
  def deleteSystemDefaultTeamMailboxShouldFail(server: GuiceJamesServer): Unit = {
    // Given a team mailbox & bob as a member
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val mailboxProbe = server.getProbe(classOf[MailboxProbeImpl])
    val mailboxSystemIdMap: Map[String, String] = teamMailbox.defaultMailboxPaths
      .map(mb => mb.getName)
      .filter(name => !name.equals("marketing"))
      .map(name => (name, mailboxProbe.getMailboxId(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", DOMAIN).asString(), name).serialize()))
      .toMap

    val response = `given`().log().all()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": [
                |    "urn:ietf:params:jmap:core",
                |    "urn:ietf:params:jmap:mail",
                |    "urn:apache:james:params:jmap:mail:shares"],
                |  "methodCalls": [[
                |           "Mailbox/set",
                |           {
                |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                |                "destroy": ["${mailboxSystemIdMap.values.toList.mkString("\",\"")}"]
                |           },
                |    "c1"
                |       ]]
                |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body().asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Mailbox/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "oldState": "$${json-unit.ignore}",
           |                "newState": "$${json-unit.ignore}",
           |                "notDestroyed": {
           |                    "${mailboxSystemIdMap("marketing.Drafts")}": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.Drafts'"
           |                    },
           |                    "${mailboxSystemIdMap("marketing.Sent")}": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.Sent'"
           |                    },
           |                    "${mailboxSystemIdMap("marketing.INBOX")}": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.INBOX'"
           |                    },
           |                    "${mailboxSystemIdMap("marketing.Trash")}": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.Trash'"
           |                    },
           |                    "${mailboxSystemIdMap("marketing.Templates")}": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.Templates'"
           |                    },
           |                    "${mailboxSystemIdMap("marketing.Outbox")}": {
           |                        "type": "invalidArguments",
           |                        "description": "user 'bob@domain.tld' is not allowed to delete the mailbox '#TeamMailbox:team-mailbox@domain.tld:marketing.Outbox'"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def movingASystemMailboxOfTeamMailboxShouldFail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.inboxPath)

    val id2 = mailboxId(server, teamMailbox.sentPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "$id1": {
         |                      "parentId": "$id2"
         |                    }
         |                }
         |           },
         |    "c1"
         |       ]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |  "sessionState":"2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |  "methodResponses":[[
           |    "Mailbox/set",
           |    {
           |      "accountId":"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |      "notUpdated":{
           |        "$id1":{
           |          "type":"forbidden",
           |          "description":"Invalid change to a delegated mailbox"
           |        }
           |      }
           |    },
           |    "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def delegatingATeamMailboxShouldFail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.inboxPath)

    val request =
      s"""{
         |   "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares" ],
         |   "methodCalls": [
         |       [
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "$id1": {
         |                      "sharedWith": {
         |                        "${CEDRIC.asString()}":["r", "l"]
         |                      }
         |                    }
         |                }
         |           },
         |    "c1"
         |       ],
         |       ["Mailbox/get",
         |         {
         |           "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |           "properties": ["id", "rights"],
         |           "ids": ["$id1"]
         |          },
         |       "c2"]
         |   ]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Mailbox/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notUpdated": {
           |                    "$id1": {
           |                        "type": "forbidden",
           |                        "description": "Invalid change to a delegated mailbox"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Mailbox/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "id": "$id1",
           |                        "rights": {
           |                            "bob@domain.tld": ["e", "i", "l", "p", "r", "s", "t", "w"]
           |                        }
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def subscribingATeamMailboxShouldSucceed(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |           "Mailbox/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "$id1": {
         |                      "isSubscribed": true
         |                    }
         |                }
         |           },
         |    "c1"], [
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$id1"]
         |    },
         |    "c2"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState", "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Mailbox/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "updated": {
           |                    "$id1": {}
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Mailbox/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "id": "$id1",
           |                        "name": "marketing",
           |                        "sortOrder": 1000,
           |                        "totalEmails": 0,
           |                        "unreadEmails": 0,
           |                        "totalThreads": 0,
           |                        "unreadThreads": 0,
           |                        "myRights": {
           |                            "mayReadItems": true,
           |                            "mayAddItems": true,
           |                            "mayRemoveItems": true,
           |                            "maySetSeen": true,
           |                            "maySetKeywords": true,
           |                            "mayCreateChild": false,
           |                            "mayRename": false,
           |                            "mayDelete": false,
           |                            "maySubmit": false
           |                        },
           |                        "isSubscribed": true,
           |                        "namespace": "TeamMailbox[marketing@domain.tld]",
           |                        "rights": {
           |                            "bob@domain.tld": ["e", "i",  "l", "p", "r", "s", "t", "w"]
           |                        }
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  private def provisionSystemMailboxes(server: GuiceJamesServer): State = {
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
      .when
      .post
      .`then`
      .statusCode(SC_OK)
      .contentType(JSON)

    //Wait until all the system mailboxes are created
    val request2 =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [[
         |    "Mailbox/changes",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${State.INITIAL.getValue.toString}"
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted { () =>
      val response1 = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request2)
      .when
        .post
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract
        .body
        .asString

      val createdSize = Json.parse(response1)
        .\("methodResponses")
        .\(0).\(1)
        .\("created")
        .get.asInstanceOf[JsArray].value.size

      val systemMailboxCount = 7
      assertThat(createdSize).isEqualTo(systemMailboxCount)
    }

    jmapGuiceProbe.getLatestMailboxState(AccountId.fromUsername(BOB))
  }

  @Test
  def addMemberTriggersAMailboxChange(server: GuiceJamesServer): Unit = {
    val oldState = provisionSystemMailboxes(server)

    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)
    val id2 = mailboxId(server, teamMailbox.inboxPath)
    val id3 = mailboxId(server, teamMailbox.sentPath)
    val id4 = mailboxId(server, teamMailbox.mailboxPath("Outbox"))
    val id5 = mailboxId(server, teamMailbox.mailboxPath("Drafts"))
    val id6 = mailboxId(server, teamMailbox.mailboxPath("Trash"))
    val id7 = mailboxId(server, teamMailbox.mailboxPath("Templates"))

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Mailbox/changes", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Mailbox/changes",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "hasMoreChanges": false,
           |                "updatedProperties": null,
           |                "created": [],
           |                "updated": ["$id1", "$id2", "$id3", "$id4", "$id5", "$id6", "$id7"],
           |                "destroyed": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  private def waitForNextState(server: GuiceJamesServer, accountId: AccountId, initialState: State): State = {
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    awaitAtMostTenSeconds.untilAsserted {
      () => assertThat(jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)).isNotEqualTo(initialState)
    }

    jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)
  }

  private def waitForNextEmailState(server: GuiceJamesServer, accountId: AccountId, initialState: State): State = {
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    awaitAtMostTenSeconds.untilAsserted {
      () => assertThat(jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)).isNotEqualTo(initialState)
    }

    jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)
  }

  @Test
  def receivingAMailShouldTriggerAStateChange(server: GuiceJamesServer): Unit = {
    val originalState = provisionSystemMailboxes(server)

    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val id2 = mailboxId(server, teamMailbox.inboxPath)

    val id3 = mailboxId(server, teamMailbox.sentPath)

    val oldState = waitForNextState(server, AccountId.fromUsername(BOB), originalState)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))

    waitForNextState(server, AccountId.fromUsername(BOB), oldState)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Mailbox/changes", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue}"
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].oldState", "methodResponses[0][1].newState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Mailbox/changes",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "hasMoreChanges": false,
           |                "updatedProperties": ["totalEmails","unreadEmails","totalThreads","unreadThreads"],
           |                "created": [],
           |                "updated": ["$id2"],
           |                "destroyed": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def emailQueryShouldReturnTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/query", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {}
         |    }, "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted(() => {
      val response: String = `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(request)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body()
        .asString()

      assertThatJson(response)
        .whenIgnoringPaths("methodResponses[0][1].queryState")
        .isEqualTo(
          s"""{
             |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
             |    "methodResponses": [
             |        [
             |            "Email/query",
             |            {
             |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |                "canCalculateChanges": false,
             |                "ids": ["$messageId"],
             |                "position": 0,
             |                "limit": 256
             |            },
             |            "c1"
             |        ]
             |    ]
             |}""".stripMargin)
    })
  }

  @Test
  def emailQueryShouldNotReturnTeamMailboxEmailWhenNoShare(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail"],
         |  "methodCalls": [["Email/query", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {}
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].queryState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "canCalculateChanges": false,
           |                "ids": [],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailQueryWithInMailboxFilterAndSortShouldNotReturnTeamMailboxEmailWhenNoShareExtension(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    val teamMailboxInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", DOMAIN).asString(), s"marketing.${MailboxConstants.INBOX}")

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail"
         |	],
         |	"methodCalls": [
         |		["Email/query", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"filter": {
         |				"inMailbox": "${teamMailboxInboxId.serialize()}"
         |			},
         |			"sort": [{
         |				"isAscending": false,
         |				"property": "receivedAt"
         |			}]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].queryState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "canCalculateChanges": false,
           |                "ids": [],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailQueryWithInMailboxFilterAndSortShouldReturnTeamMailboxEmailWhenShareExtension(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    val teamMailboxInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", DOMAIN).asString(), s"marketing.${MailboxConstants.INBOX}")

    val request =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:mail",
         |		"urn:apache:james:params:jmap:mail:shares"
         |	],
         |	"methodCalls": [
         |		["Email/query", {
         |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			"filter": {
         |				"inMailbox": "${teamMailboxInboxId.serialize()}"
         |			},
         |			"sort": [{
         |				"isAscending": false,
         |				"property": "receivedAt"
         |			}]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].queryState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/query",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "canCalculateChanges": false,
           |                "ids": ["$messageId"],
           |                "position": 0,
           |                "limit": 256
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailGetShouldReturnTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$messageId"],
         |      "properties":["subject"]
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "subject": "test",
           |                        "id": "$messageId"
           |                    }
           |                ]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def downloadShouldReturnTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/$messageId")
    .`then`
      .statusCode(SC_OK)
      .contentType("message/rfc822")
      .extract
      .body
      .asString

    val outputStream = new ByteArrayOutputStream()
    new DefaultMessageWriter().writeMessage(message, outputStream)
    val expectedResponse: String = new String(outputStream.toByteArray)
    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent(expectedResponse)
  }

  @Test
  def uploadAndImportShouldReturnTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val outputStream = new ByteArrayOutputStream()
    new DefaultMessageWriter().writeMessage(message, outputStream)
    val content: String = new String(outputStream.toByteArray)

    val blobId: String = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(content)
    .when
      .post(s"/upload/$ACCOUNT_ID")
    .`then`
      .statusCode(SC_CREATED)
      .extract
      .body
      .jsonPath()
      .getString("blobId")

    val receivedAt = ZonedDateTime.now().minusDays(1)
    val receivedAtString = UTCDate(receivedAt).asUTC.format(UTC_DATE_FORMAT)
    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
                     |  "methodCalls": [
                     |    ["Email/import",
                     |      {
                     |        "accountId": "$ACCOUNT_ID",
                     |        "emails": {
                     |           "K39": {
                     |             "blobId": "$blobId",
                     |             "mailboxIds": {
                     |               "$id1": true
                     |             },
                     |             "keywords": {
                     |               "toto": true
                     |             },
                     |             "receivedAt": "$receivedAtString"
                     |           }
                     |         }
                     |      },
                     |      "c1"],
                     |    ["Email/get",
                     |     {
                     |       "accountId": "$ACCOUNT_ID",
                     |       "ids": ["#K39"],
                     |       "properties": ["mailboxIds", "keywords", "receivedAt"]
                     |     },
                     |     "c2"]
                     |  ]
                     |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state",
        "methodResponses[0][1].created.K39.id", "methodResponses[0][1].created.K39.threadId", "methodResponses[0][1].created.K39.blobId",
        "methodResponses[0][1].created.K39.size", "methodResponses[1][1].list[0].id")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/import",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "created": {
           |                    "K39": {}
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "keywords": {"toto": true},
           |                        "mailboxIds": {"$id1": true},
           |                        "receivedAt": "$receivedAtString"
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetShouldDestroyTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "destroy": ["$messageId"]
         |    }, "c1"], ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$messageId"],
         |      "properties":["subject"]
         |    }, "c2"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "destroyed": ["$messageId"]
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": ["$messageId"],
           |                "state": "14ee6150-95ea-44dc-bf1b-e50953f43404",
           |                "list": []
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetShouldUpdateFlagsForTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "$messageId": {
         |          "keywords": { "Custom": true }
         |        }
         |      }
         |    }, "c1"], ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$messageId"],
         |      "properties":["keywords"]
         |    }, "c2"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "updated": {"$messageId": null}
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "keywords": {"custom": true},
           |                        "id": "$messageId"
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetShouldMoveTeamMailboxEmailOut(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB))
      .serialize()

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "$messageId": {
         |          "mailboxIds": { "$id": true }
         |        }
         |      }
         |    }, "c1"], ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$messageId"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "updated": {"$messageId": null}
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "mailboxIds":{"$id":true},
           |                        "id": "$messageId"
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetShouldMoveTeamMailboxEmailIn(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id = mailboxId(server, teamMailbox.mailboxPath)

    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(MailboxPath.inbox(BOB))
    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), MailboxPath.inbox(BOB), AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "$messageId": {
         |          "mailboxIds": { "$id": true }
         |        }
         |      }
         |    }, "c1"], ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["$messageId"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "updated": {"$messageId": null}
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "mailboxIds":{"$id":true},
           |                        "id": "$messageId"
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailSetShouldCreateTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "create": {
         |        "K39": {
         |          "mailboxIds": {"$id1":true}
         |        }
         |      }
         |    }, "c1"], ["Email/get", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["#K39"],
         |      "properties":["mailboxIds"]
         |    }, "c2"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state",
        "methodResponses[0][1].created.K39.id", "methodResponses[0][1].created.K39.threadId", "methodResponses[0][1].created.K39.blobId",
        "methodResponses[0][1].created.K39.size", "methodResponses[1][1].list[0].id")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "created": {
           |                    "K39": {
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Email/get",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notFound": [],
           |                "list": [
           |                    {
           |                        "mailboxIds": {"$id1": true}
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def emailChangesShouldReturnTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val oldState = server.getProbe(classOf[JmapGuiceProbe]).getLatestMailboxStateWithDelegation(AccountId.fromUsername(BOB))

    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    waitForNextEmailState(server, AccountId.fromUsername(BOB), oldState)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/changes", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "sinceState": "${oldState.getValue.toString}"
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/changes",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "hasMoreChanges": false,
           |                "created": ["$messageId"],
           |                "updated": [],
           |                "destroyed": []
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def threadGetShouldHandleTeamMailboxEmail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val message1: Message = Message.Builder
      .of
      .setSubject("test")
      .setMessageId("abc")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val result = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessageAndGetAppendResult(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message1))
    val messageId1 = result.getId.getMessageId.serialize()
    val threadId = result
      .getThreadId
      .getBaseMessageId
      .serialize()
    val message2: Message = Message.Builder
      .of
      .setSubject("test")
      .setMessageId("abc")
      .setField(new RawField("In-Reply-To", "abc"))
      .setBody("testmail", StandardCharsets.UTF_8)
      .build
    val messageId2 = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message2))
      .getMessageId
      .serialize()

    awaitAtMostTenSeconds.untilAsserted(()=> {
      val response: String = `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(s"""{
                 |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
                 |  "methodCalls": [["Thread/get", {
                 |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                 |      "ids": ["$threadId"]
                 |    }, "c1"]]
                 |}""".stripMargin)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .contentType(JSON)
        .extract()
        .body()
        .asString()

      assertThatJson(response)
        .withOptions(IGNORING_ARRAY_ORDER)
        .whenIgnoringPaths("methodResponses[0][1].state")
        .isEqualTo(
          s"""
             |{
             |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
             |    "methodResponses": [
             |        [
             |            "Thread/get",
             |            {
             |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |                "list": [
             |                    {
             |                        "id": "$threadId",
             |                        "emailIds": ["$messageId1", "$messageId2"]
             |                    }
             |                ],
             |                "notFound": []
             |            },
             |            "c1"
             |        ]
             |    ]
             |}
             |""".stripMargin)
    })
  }

  @Test
  def webSocketShouldPushTeamMailboxStateChanges(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val bobPath = MailboxPath.inbox(BOB)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobPath)
    val accountId: AccountId = AccountId.fromUsername(BOB)
    Thread.sleep(100)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val response: Either[String, List[String]] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, List[String]] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["Mailbox", "Email"]
                |}""".stripMargin))

            Thread.sleep(100)

            server.getProbe(classOf[MailboxProbeImpl])
              .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
              .getMessageId.serialize()

            Thread.sleep(100)
            ws.receiveMessageInTimespan(scala.concurrent.duration.Duration(2000, MILLISECONDS))
        })
        .send(backend)
        .body

    Thread.sleep(100)
    val jmapGuiceProbe: JmapGuiceProbe = server.getProbe(classOf[JmapGuiceProbe])
    val emailState: State = jmapGuiceProbe.getLatestEmailStateWithDelegation(accountId)
    val mailboxState: State = jmapGuiceProbe.getLatestMailboxStateWithDelegation(accountId)

    val globalState: String = PushState.fromOption(Some(UuidState.fromJava(mailboxState)), Some(UuidState.fromJava(emailState))).get.value

    assertThatJson(response.toOption.get.last)
      .isEqualTo(
        s"""{
           |  "@type":"StateChange",
           |  "changed":{
           |    "$ACCOUNT_ID":{
           |      "Email": "${emailState.getValue}",
           |      "Mailbox":"${mailboxState.getValue}"}
           |    },
           |    "pushState":"$globalState"
           |  }
           |}""".stripMargin)
  }

  @Test
  def teamMailboxesShouldComputeQuotas(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id = mailboxId(server, teamMailbox.inboxPath)

    val bobPath = MailboxPath.inbox(BOB)
    val bobInboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(bobPath)
      .serialize()

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    // 2 messages on the team mailbox
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    // 1 message in BOB inbox
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), bobPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(teamMailbox.quotaRoot, QuotaCountLimit.count(4L))
    quotaProbe.setMaxMessageCount(quotaProbe.getQuotaRoot(bobPath), QuotaCountLimit.count(5L))
    quotaProbe.setGlobalMaxStorage(QuotaSizeLimit.size(100 * 1024 * 1024))

    val request =s"""{
                    |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail",
                    |    "urn:apache:james:params:jmap:mail:shares", "urn:apache:james:params:jmap:mail:quota"],
                    |  "methodCalls": [[
                    |    "Mailbox/get",
                    |    {
                    |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |      "ids": ["$id", "$bobInboxId"],
                    |      "properties": ["quotas"]
                    |    },
                    |    "c1"]]
                    |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state")
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Mailbox/get", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"notFound": [],
           |			"list": [{
           |				"id": "$bobInboxId",
           |				"quotas": {
           |					"#private&bob@domain.tld": {
           |						"Storage": {
           |							"used": 85,
           |							"max": 104857600
           |						},
           |						"Message": {
           |							"used": 1,
           |							"max": 5
           |						}
           |					}
           |				}
           |			}, {
           |				"id": "$id",
           |				"quotas": {
           |					"#TeamMailbox&marketing@domain.tld": {
           |						"Storage": {
           |							"used": 170,
           |							"max": 104857600
           |						},
           |						"Message": {
           |							"used": 2,
           |							"max": 4
           |						}
           |					}
           |				}
           |			}]
           |		}, "c1"]
           |	]
           |}""".stripMargin)
  }

  @Test
  def teamMailboxesShouldEnforceQuotasUponCreate(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
    val id1 = mailboxId(server, teamMailbox.mailboxPath)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(teamMailbox.quotaRoot, QuotaCountLimit.count(2L))

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "create": {
         |        "K39": {
         |          "mailboxIds": {"$id1":true}
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state",
        "methodResponses[0][1].created.K39.id", "methodResponses[0][1].created.K39.threadId", "methodResponses[0][1].created.K39.blobId",
        "methodResponses[0][1].created.K39.size", "methodResponses[1][1].list[0].id")
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Email/set", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"notCreated": {
           |				"K39": {
           |					"type": "overQuota",
           |					"description": "You have too many messages in #TeamMailbox&marketing@domain.tld"
           |				}
           |			}
           |		}, "c1"]
           |	]
           |}""".stripMargin)
  }

  @Test
  def teamMailboxesShouldEnforceQuotasUponCopy(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
    val id1 = mailboxId(server, teamMailbox.mailboxPath)
    val id2 = mailboxId(server, teamMailbox.inboxPath)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val quotaProbe = server.getProbe(classOf[QuotaProbesImpl])
    quotaProbe.setMaxMessageCount(teamMailbox.quotaRoot, QuotaCountLimit.count(2L))

    server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()
    val messageId = server.getProbe(classOf[MailboxProbeImpl])
      .appendMessage(BOB.asString(), teamMailbox.inboxPath, AppendCommand.from(message))
      .getMessageId.serialize()

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:mail",
         |    "urn:apache:james:params:jmap:mail:shares"],
         |  "methodCalls": [["Email/set", {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "update": {
         |        "$messageId": {
         |          "mailboxIds": {"$id1":true, "$id2":true}
         |        }
         |      }
         |    }, "c1"]]
         |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "Email/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "notUpdated": {
           |                    "$messageId": {
           |                        "type": "overQuota",
           |                        "description": "You have too many messages in #TeamMailbox&marketing@domain.tld"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def teamMailboxShouldBeIndexedAsDomainContactUponCreation(): Unit = {
    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes")
    .when()
      .put("/hiring")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    val bobRequest =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "filter": {"text":"hiring"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    awaitAtMostTenSeconds.untilAsserted(()=> {
      val response = `given`
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(bobRequest)
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
             |	"sessionState": "${SESSION_STATE.value}",
             |	"methodResponses": [[
             |			"TMailContact/autocomplete",
             |			{
             |				"accountId": "$BOB_ACCOUNT_ID",
             |				"list": [{
             |					"id": "$${json-unit.ignore}",
             |					"firstname": "hiring",
             |					"surname": "",
             |					"emailAddress": "hiring@domain.tld"
             |				}],
             |				"limit": 256
             |			},
             |			"c1"]]
             |}""".stripMargin)
    })
  }

  @Test
  def teamMailboxDeletionShouldRemoveAssociatedDomainContact(): Unit = {
    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes")
    .when()
      .put("/hiring")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes")
    .when()
      .delete("/hiring")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    val bobRequest =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "$BOB_ACCOUNT_ID",
         |      "filter": {"text":"hiring"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(bobRequest)
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
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [[
           |			"TMailContact/autocomplete",
           |			{
           |				"accountId": "$BOB_ACCOUNT_ID",
           |				"list": [],
           |				"limit": 256
           |			},
           |			"c1"]]
           |}""".stripMargin)
  }

  @Test
  def shouldRenameCustomMailboxSuccessByMember(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val teamMailboxId: String = mailboxId(server, teamMailbox.mailboxPath)

    val childMailboxId: String = createTeamMailbox(teamMailboxId,"ChildOfTopMailbox" )

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "update": {
               |                    "$childMailboxId": {
               |                      "name": "newChild1"
               |                    }
               |                }
               |           },
               |    "c1"
               |       ]]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].updated", hasKey(childMailboxId))

    val response : String = given()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/get",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "ids": ["$childMailboxId"]
               |           },
               |    "c1"
               |       ]]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[0][1].state",
        "methodResponses[1][1].state",
        "methodResponses[0][1].newState",
        "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""
          |{
          |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
          |    "methodResponses": [
          |        [
          |            "Mailbox/get",
          |            {
          |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |                "state": "2eb3e9ee-45d7-429d-b8d8-9a9a0c44d577",
          |                "list": [
          |                    {
          |                        "id": "$childMailboxId",
          |                        "name": "newChild1",
          |                        "parentId": "$teamMailboxId",
          |                        "sortOrder": 1000,
          |                        "totalEmails": 0,
          |                        "unreadEmails": 0,
          |                        "totalThreads": 0,
          |                        "unreadThreads": 0,
          |                        "myRights": {
          |                            "mayReadItems": true,
          |                            "mayAddItems": true,
          |                            "mayRemoveItems": true,
          |                            "maySetSeen": true,
          |                            "maySetKeywords": true,
          |                            "mayCreateChild": false,
          |                            "mayRename": true,
          |                            "mayDelete": true,
          |                            "maySubmit": false
          |                        },
          |                        "isSubscribed": true,
          |                        "namespace": "TeamMailbox[marketing@domain.tld]",
          |                        "rights": {
          |                            "bob@domain.tld": [ "e", "i", "l", "p", "r", "s", "t", "w", "x" ]
          |                       }
          |                    }
          |                ],
          |                "notFound": [
          |                    
          |                ]
          |            },
          |            "c1"
          |        ]
          |    ]
          |}""".stripMargin)
  }

  @Test
  def shouldNotRenameMailboxWhenUserIsNotMember(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val teamMailboxId: String = mailboxId(server, teamMailbox.mailboxPath)

    val childMailboxId: String = createTeamMailbox(teamMailboxId,"ChildOfTopMailbox" )

    server.getProbe(classOf[TeamMailboxProbe])
      .removeMember(teamMailbox, BOB)

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "update": {
               |                    "$childMailboxId": {
               |                      "name": "newChild1"
               |                    }
               |                }
               |           },
               |    "c1"
               |       ]]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notUpdated", hasKey(childMailboxId))
      .body("methodResponses[0][1].notUpdated." + childMailboxId, jsonEquals(
        s"""{
           |  "type": "notFound",
           |  "description": "$childMailboxId can not be found"
           |}""".stripMargin))
  }

  @Test
  def shouldNotRenameToExistMailboxPath(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val teamMailboxId: String = mailboxId(server, teamMailbox.mailboxPath)
    val childMailboxId1: String = createTeamMailbox(teamMailboxId, "ChildOfTopMailbox")
    val existMailboxName = "ChildOfTopMailboxExist"
    val childMailboxId2: String = createTeamMailbox(teamMailboxId, existMailboxName)

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "urn:ietf:params:jmap:mail",
           |    "urn:apache:james:params:jmap:mail:shares"],
           |  "methodCalls": [[
           |           "Mailbox/set",
           |           {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "update": {
           |                    "$childMailboxId1": {
           |                      "name": "$existMailboxName"
           |                    }
           |                }
           |           },
           |    "c1"
           |       ]]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].notUpdated", hasKey(childMailboxId1));
  }

  @Test
  def shouldMoveMailboxToAnotherTeamMailboxSuccessByMember(server: GuiceJamesServer): Unit = {
    val marketingMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    val saleMailbox = TeamMailbox(DOMAIN, TeamMailboxName("sale"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(marketingMailbox)
      .create(saleMailbox)
      .addMember(marketingMailbox, BOB)
      .addMember(saleMailbox, BOB)

    val marketingMailboxId: String = mailboxId(server, marketingMailbox.mailboxPath)
    val saleMailboxId: String = mailboxId(server, saleMailbox.mailboxPath)

    val childMarketingMailboxId: String = createTeamMailbox(marketingMailboxId, "child1")

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "update": {
               |                  "$childMarketingMailboxId": {
               |                      "parentId": "$saleMailboxId"
               |                   }
               |                }
               |           },
               |       "c1"]
               |    ]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].updated", hasKey(childMarketingMailboxId))
  }

  @Test
  def shouldMoveCustomFolderFromTeamMailboxToOwnerMailbox(server: GuiceJamesServer): Unit = {
    val marketingMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(marketingMailbox)
      .addMember(marketingMailbox, BOB)

    val marketingMailboxId: String = mailboxId(server, marketingMailbox.mailboxPath)
    val childMarketingMailboxId: String = createTeamMailbox(marketingMailboxId, "child1")

    val inboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB)).serialize()

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "update": {
               |                  "$childMarketingMailboxId": {
               |                      "parentId": "$inboxId",
               |                      "name": "newName1"
               |                   }
               |                }
               |           },
               |       "c1"]
               |    ]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].updated", hasKey(childMarketingMailboxId))

    given()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/get",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "ids": ["$childMarketingMailboxId"]
               |           },
               |    "c1"
               |       ]]
               |}""".stripMargin)
      .when()
      .post()
      .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list[0].name", equalTo("newName1"))
      .body("methodResponses[0][1].list[0].namespace", equalTo("Personal"))
      .body("methodResponses[0][1].list[0].parentId", equalTo(inboxId))
  }

  @Test
  def shouldMoveOwnerMailboxToCustomFolderInTeamMailbox(server: GuiceJamesServer): Unit = {
    val marketingMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(marketingMailbox)
      .addMember(marketingMailbox, BOB)

    val marketingMailboxId: String = mailboxId(server, marketingMailbox.mailboxPath)
    val ownerMailboxId = server.getProbe(classOf[MailboxProbeImpl])
      .createMailbox(MailboxPath.inbox(BOB).child("childOfOwner", '.')).serialize()

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/set",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "update": {
               |                  "$ownerMailboxId": {
               |                      "parentId": "$marketingMailboxId",
               |                      "name": "newName1",
               |                      "sharedWith": {
               |                        "${BOB.asString()}": ["l", "r"]
               |                      }
               |                   }
               |                }
               |           },
               |       "c1"]
               |    ]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .body("methodResponses[0][1].updated", hasKey(ownerMailboxId))

    Thread.sleep(2000)

    given()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": [
               |    "urn:ietf:params:jmap:core",
               |    "urn:ietf:params:jmap:mail",
               |    "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |           "Mailbox/get",
               |           {
               |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |                "ids": ["$ownerMailboxId"]
               |           },
               |    "c1"
               |       ]]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].list[0].name", equalTo("newName1"))
      .body("methodResponses[0][1].list[0].namespace", equalTo("TeamMailbox[marketing@domain.tld]"))
      .body("methodResponses[0][1].list[0].parentId", equalTo(marketingMailboxId))
  }

  @Test
  def extraSenderCanSetIdentityForTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)

    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes/hiring/extraSenders")
    .when()
      .put(s"/${BOB.asString()}")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:submission"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Identity/set",
         |      {
         |        "accountId": "$BOB_ACCOUNT_ID",
         |        "create": {
         |          "4f29": {
         |            "name": "test",
         |            "email": "hiring@domain.tld",
         |            "textSignature": "Some text signature",
         |            "htmlSignature": "<p>Some html signature</p>"
         |          }
         |        }
         |      },
         |      "c0"
         |    ]
         |  ]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
           |  "methodResponses": [
           |    [
           |      "Identity/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "${SESSION_STATE.value}",
           |        "created": {
           |          "4f29": {
           |            "id": "$${json-unit.ignore}",
           |            "mayDelete": true
           |          }
           |        }
           |      },
           |      "c0"
           |    ]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def extraSenderCanSubmitEmailAsTeamMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)

    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes/marketing/extraSenders")
    .when()
      .put(s"/${BOB.asString()}")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    val message: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(s"marketing@${DOMAIN.asString()}")
      .setFrom(s"marketing@${DOMAIN.asString()}")
      .setTo(BOB.asString)
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

    val bobDraftsPath = MailboxPath.forUser(BOB, DefaultMailboxes.DRAFTS)
    server.getProbe(classOf[MailboxProbeImpl]).createMailbox(bobDraftsPath)
    val messageId: MessageId = server.getProbe(classOf[MailboxProbeImpl]).appendMessage(BOB.asString(), bobDraftsPath, AppendCommand.builder()
      .build(message))
      .getMessageId

    val request = s"""{
                     |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:ietf:params:jmap:submission"],
                     |  "methodCalls": [
                     |     ["EmailSubmission/set", {
                     |       "accountId": "$ACCOUNT_ID",
                     |       "create": {
                     |         "k1490": {
                     |           "emailId": "${messageId.serialize}",
                     |           "envelope": {
                     |             "mailFrom": {"email": "marketing@${DOMAIN.asString()}"},
                     |             "rcptTo": [{"email": "${BOB.asString}"}]
                     |           }
                     |         }
                     |    }
                     |  }, "c1"]]
                     |}""".stripMargin

    val response: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |    "sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |    "methodResponses": [
           |        [
           |            "EmailSubmission/set",
           |            {
           |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "created": {
           |                    "k1490": "$${json-unit.ignore}"
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def extraSenderRemovedCanNoLongerSetIdentity(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("hiring"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)

    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes/hiring/extraSenders")
    .when()
      .put(s"/${BOB.asString()}")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    `given`
      .spec(webAdminApi)
      .basePath(s"/domains/${DOMAIN.asString()}/team-mailboxes/hiring/extraSenders")
    .when()
      .delete(s"/${BOB.asString()}")
    .`then`()
      .statusCode(HttpStatus.SC_NO_CONTENT)

    val request =
      s"""{
         |  "using": [
         |    "urn:ietf:params:jmap:core",
         |    "urn:ietf:params:jmap:submission"
         |  ],
         |  "methodCalls": [
         |    [
         |      "Identity/set",
         |      {
         |        "accountId": "$BOB_ACCOUNT_ID",
         |        "create": {
         |          "4f29": {
         |            "name": "test",
         |            "email": "hiring@domain.tld"
         |          }
         |        }
         |      },
         |      "c0"
         |    ]
         |  ]
         |}""".stripMargin

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
           |  "methodResponses": [
           |    [
           |      "Identity/set",
           |      {
           |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |        "newState": "${SESSION_STATE.value}",
           |        "notCreated": {
           |          "4f29": {
           |            "type": "forbiddenFrom",
           |            "description": "Can not send from hiring@domain.tld"
           |          }
           |        }
           |      },
           |      "c0"
           |    ]
           |  ]
           |}""".stripMargin)
  }

  private def createTeamMailbox(parentId: String, name: String): String = {
    val mailboxId: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(s"""{
               |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail", "urn:apache:james:params:jmap:mail:shares"],
               |  "methodCalls": [[
               |    "Mailbox/set",
               |    {
               |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
               |      "create": { "K39": { "name": "$name", "parentId": "$parentId" } }
               |    },
               |    "c1"
               |  ]]
               |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .extract()
      .path("methodResponses[0][1].created.K39.id")

    awaitToUpdateRightByListener(mailboxId)
    mailboxId
  }

  private def awaitToUpdateRightByListener(mailboxId: String) = {
    // Verify that bob has the right to delete the mailbox
    awaitAtMostTenSeconds.untilAsserted(() => {
      `given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(
          s"""{
             |    "using": [
             |      "urn:ietf:params:jmap:core",
             |      "urn:ietf:params:jmap:mail",
             |      "urn:apache:james:params:jmap:mail:shares"],
             |    "methodCalls": [
             |             ["Mailbox/get",
             |           {
             |             "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |             "ids": ["$mailboxId"]
             |            },
             |         "c2"]
             |
             |      ]
             |  }""".stripMargin)
      .when()
        .post()
      .`then`
        .statusCode(SC_OK)
        .body("methodResponses[0][1].list[0].myRights.mayDelete", equalTo(true))
    })
  }

  private def authenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .auth.basic(BOB.asString(), BOB_PASSWORD)
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }
}
