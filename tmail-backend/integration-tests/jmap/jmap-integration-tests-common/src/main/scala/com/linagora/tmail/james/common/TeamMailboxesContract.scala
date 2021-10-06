package com.linagora.tmail.james.common

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxName, TeamMailboxRepository, TeamMailboxRepositoryImpl}
import eu.timepit.refined.auto._
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import javax.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.modules.MailboxProbeImpl
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

class TeamMailboxProbeModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TeamMailboxRepository]).to(classOf[TeamMailboxRepositoryImpl])

    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[TeamMailboxProbe])
  }
}

class TeamMailboxProbe @Inject()(teamMailboxRepository: TeamMailboxRepository) extends GuiceProbe {
  def create(teamMailbox: TeamMailbox): TeamMailboxProbe = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    this
  }

  def addMember(teamMailbox: TeamMailbox, member: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.addMember(teamMailbox, member)).block()
    this
  }
}

trait TeamMailboxesContract {

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
  }

  @Test
  def mailboxGetShouldListBaseMailbox(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

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
           |          "isSubscribed": false,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["i", "l", "r", "s", "t", "w"]}
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.inboxPath.getNamespace, teamMailbox.inboxPath.getUser.asString(), teamMailbox.inboxPath.getName)
      .serialize()

    val id2 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

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
           |          "isSubscribed": false,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["i", "l", "r", "s", "t", "w"]}
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.sentPath.getNamespace, teamMailbox.sentPath.getUser.asString(), teamMailbox.sentPath.getName)
      .serialize()

    val id2 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

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
           |          "isSubscribed": false,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["i", "l", "r", "s", "t", "w"]}
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.sentPath.getNamespace, teamMailbox.sentPath.getUser.asString(), teamMailbox.sentPath.getName)
      .serialize()

    val id2 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

    val id3 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.inboxPath.getNamespace, teamMailbox.inboxPath.getUser.asString(), teamMailbox.inboxPath.getName)
      .serialize()

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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.sentPath.getNamespace, teamMailbox.sentPath.getUser.asString(), teamMailbox.sentPath.getName)
      .serialize()

    val id2 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

    val id3 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.inboxPath.getNamespace, teamMailbox.inboxPath.getUser.asString(), teamMailbox.inboxPath.getName)
      .serialize()

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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
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
  def mailboxGetShouldNotListRightsOfOthers(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)
      .addMember(teamMailbox, CEDRIC)

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

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
           |          "isSubscribed": false,
           |          "namespace": "TeamMailbox[marketing@domain.tld]",
           |          "rights": {"bob@domain.tld":["i", "l", "r", "s", "t", "w"]}
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

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
           |          "type":"notFound",
           |          "description":"#TeamMailbox:team-mailbox@domain.tld:marketing"
           |        }
           |      }
           |    },
           |    "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def movingATeamMailboxShouldFail(server: GuiceJamesServer): Unit = {
    val teamMailbox = TeamMailbox(DOMAIN, TeamMailboxName("marketing"))
    server.getProbe(classOf[TeamMailboxProbe])
      .create(teamMailbox)
      .addMember(teamMailbox, BOB)

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.inboxPath.getNamespace, teamMailbox.inboxPath.getUser.asString(), teamMailbox.inboxPath.getName)
      .serialize()

    val id2 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.sentPath.getNamespace, teamMailbox.sentPath.getUser.asString(), teamMailbox.sentPath.getName)
      .serialize()

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
           |          "type":"notFound",
           |          "description":"#TeamMailbox:team-mailbox@domain.tld:marketing.Sent.INBOX"
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.inboxPath.getNamespace, teamMailbox.inboxPath.getUser.asString(), teamMailbox.inboxPath.getName)
      .serialize()

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
           |                        "type": "invalidArguments",
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
           |                            "bob@domain.tld": ["i", "l", "r", "s", "t", "w"]
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

    val id1 = server.getProbe(classOf[MailboxProbeImpl])
      .getMailboxId(teamMailbox.mailboxPath.getNamespace, teamMailbox.mailboxPath.getUser.asString(), teamMailbox.mailboxPath.getName)
      .serialize()

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
           |                            "bob@domain.tld": ["i",  "l", "r", "s", "t", "w"]
           |                        }
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }
}
