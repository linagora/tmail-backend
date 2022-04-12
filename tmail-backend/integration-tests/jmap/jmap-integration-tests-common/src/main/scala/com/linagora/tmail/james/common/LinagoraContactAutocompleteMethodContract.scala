package com.linagora.tmail.james.common

import java.time.Duration
import java.util.concurrent.TimeUnit

import com.linagora.tmail.james.common.LinagoraContactAutocompleteMethodContract.{bobAccountId, calmlyAwait, contactA, contactB, firstnameA, firstnameB, mailAddressA, mailAddressB, surnameA, surnameB}
import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe
import com.linagora.tmail.james.jmap.contact.ContactFields
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.MailAddress
import org.apache.james.core.builder.MimeMessageBuilder
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.modules.protocols.SmtpGuiceProbe
import org.apache.james.utils.{DataProbeImpl, SMTPMessageSender}
import org.apache.mailet.base.test.FakeMail
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.{BeforeEach, Test}

object LinagoraContactAutocompleteMethodContract {
  private val bobAccountId: AccountId = AccountId.fromUsername(BOB)

  private val mailAddressA: MailAddress = new MailAddress("nobita@linagora.com")
  private val firstnameA: String = "John"
  private val surnameA: String = "Carpenter"
  private val contactA: ContactFields = ContactFields(mailAddressA, firstnameA, surnameA)

  private val mailAddressB: MailAddress = new MailAddress(s"nobito@${DOMAIN.asString}")
  private val firstnameB: String = "Marie"
  private val surnameB: String = "Carpenter"
  private val contactB: ContactFields = ContactFields(mailAddressB, firstnameB, surnameB)

  private lazy val slowPacedPollInterval: Duration = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait: ConditionFactory = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
}

trait LinagoraContactAutocompleteMethodContract {
  def awaitDocumentsIndexed(documentCount: Long): Unit

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def contactAutocompleteShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "unknownAccountId",
         |      "filter": {"text":"any"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldFailWhenMissingCapability(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"any"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "description":"Missing capability(ies): com:linagora:params:jmap:contact:autocomplete","type":"unknownMethod"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldWork(server: GuiceJamesServer): Unit = {
    val contactCreated = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(bobAccountId, contactA)

    awaitDocumentsIndexed(1)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"obi"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    [
         |      "TMailContact/autocomplete",
         |		  {
         |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |			  "list": [{
         |          "id": "${contactCreated.id.toString}",
         |				  "firstname": "$firstnameA",
         |				  "surname": "$surnameA",
         |				  "emailAddress": "$mailAddressA"
         |			  }],
         |        "limit": 256
         |		  },
         |		  "c1"
         |    ]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldReturnEmptyListWhenNoContactFound(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"obi"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |     "TMailContact/autocomplete",
                    |		  {
                    |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |			  "list": [],
                    |       "limit": 256
                    |		  },
                    |		  "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldReturnMultipleContacts(server: GuiceJamesServer): Unit = {
    val contactCreatedA = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(bobAccountId, contactA)
    val contactCreatedB = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(DOMAIN, contactB)

    awaitDocumentsIndexed(2)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"obi"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |     "TMailContact/autocomplete",
                    |		  {
                    |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |			  "list": [{
                    |         "id": "${contactCreatedA.id.toString}",
                    |				  "firstname": "$firstnameA",
                    |				  "surname": "$surnameA",
                    |				  "emailAddress": "$mailAddressA"
                    |			  },
                    |       {
                    |         "id": "${contactCreatedB.id.toString}",
                    |				  "firstname": "$firstnameB",
                    |				  "surname": "$surnameB",
                    |				  "emailAddress": "$mailAddressB"
                    |			  }],
                    |       "limit": 256
                    |		  },
                    |		  "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldReturnOnlyMatches(server: GuiceJamesServer): Unit = {
    val contactCreatedA = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(bobAccountId, contactA)
    server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(DOMAIN, contactB)

    awaitDocumentsIndexed(2)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"nobita"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |     "TMailContact/autocomplete",
                    |		  {
                    |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |			  "list": [{
                    |         "id": "${contactCreatedA.id.toString}",
                    |				  "firstname": "$firstnameA",
                    |				  "surname": "$surnameA",
                    |				  "emailAddress": "$mailAddressA"
                    |			  }],
                    |       "limit": 256
                    |		  },
                    |		  "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldReturnOnFirstnameSearch(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(bobAccountId, contactA)
    val contactCreatedB = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(DOMAIN, contactB)

    awaitDocumentsIndexed(2)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"mari"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |     "TMailContact/autocomplete",
                    |		  {
                    |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |			  "list": [{
                    |         "id": "${contactCreatedB.id.toString}",
                    |				  "firstname": "$firstnameB",
                    |				  "surname": "$surnameB",
                    |				  "emailAddress": "$mailAddressB"
                    |			  }],
                    |       "limit": 256
                    |		  },
                    |		  "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)
  }

  @Test
  def contactAutocompleteShouldReturnOnSurname(server: GuiceJamesServer): Unit = {
    val contactCreatedA = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(bobAccountId, contactA)
    val contactCreatedB = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(DOMAIN, contactB)

    awaitDocumentsIndexed(2)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"Car"}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |     "TMailContact/autocomplete",
                    |		  {
                    |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |			  "list": [{
                    |         "id": "${contactCreatedA.id.toString}",
                    |				  "firstname": "$firstnameA",
                    |				  "surname": "$surnameA",
                    |				  "emailAddress": "$mailAddressA"
                    |			  },
                    |       {
                    |         "id": "${contactCreatedB.id.toString}",
                    |				  "firstname": "$firstnameB",
                    |				  "surname": "$surnameB",
                    |				  "emailAddress": "$mailAddressB"
                    |			  }],
                    |       "limit": 256
                    |		  },
                    |		  "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)
  }

  @Test
  def filterTextShouldBeAString(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":123}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "{\\"errors\\":[{\\"path\\":\\"obj.filter.text\\",\\"messages\\":[\\"Expecting a JsString to be representing an autocomplete text search\\"]}]}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  def filterTextShouldBeCompulsory(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {}
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "{\\"errors\\":[{\\"path\\":\\"obj.filter.text\\",\\"messages\\":[\\"error.path.missing\\"]}]}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  def filterShouldBeCompulsory(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "{\\"errors\\":[{\\"path\\":\\"obj.filter\\",\\"messages\\":[\\"error.path.missing\\"]}]}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  def shouldReturnInvalidArgumentsWhenInvalidFilterCondition(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {
         |        "unsupported_option": "blabla",
         |        "text":"obi"
         |      }
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "error",
           |            {
           |                "type": "invalidArguments",
           |                "description": "{\\"errors\\":[{\\"path\\":\\"obj.filter\\",\\"messages\\":[\\"These '[unsupported_option]' was unsupported filter options\\"]}]}"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  def limitShouldBeTakenIntoAccount(server: GuiceJamesServer): Unit = {
    val contactCreatedA = server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(bobAccountId, contactA)
    server.getProbe(classOf[JmapGuiceContactAutocompleteProbe])
      .index(DOMAIN, contactB)

    awaitDocumentsIndexed(2)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:contact:autocomplete"],
         |  "methodCalls": [[
         |    "TMailContact/autocomplete",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "filter": {"text":"obi"},
         |      "limit": 1
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .isEqualTo(s"""{
                    |  "sessionState": "${SESSION_STATE.value}",
                    |  "methodResponses": [
                    |    [
                    |     "TMailContact/autocomplete",
                    |		  {
                    |			  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
                    |			  "list": [{
                    |         "id": "${contactCreatedA.id.toString}",
                    |				  "firstname": "$firstnameA",
                    |				  "surname": "$surnameA",
                    |				  "emailAddress": "$mailAddressA"
                    |			  }]
                    |		  },
                    |		  "c1"
                    |    ]
                    |  ]
                    |}""".stripMargin)

  }

  @Test
  def contactShouldBeIndexedWhenMailing(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .addUser(ANDRE.asString, ANDRE_PASSWORD)

    val mail: FakeMail = FakeMail.builder()
      .name("mail1")
      .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
        .setSender(BOB.asString())
        .addToRecipient(ANDRE.asString())
        .setSubject("Subject 01")
        .setText("Content mail 123"))
      .sender(BOB.asString())
      .recipient(ANDRE.asString())
      .build()

    new SMTPMessageSender(DOMAIN.asString())
      .connect("127.0.0.1", server.getProbe(classOf[SmtpGuiceProbe]).getSmtpPort)
      .authenticate(BOB.asString(), BOB_PASSWORD)
      .sendMessage(mail)

    bobShouldHaveAndreContact()
  }

  private def bobShouldHaveAndreContact(): Unit = {
    val request: String =
      s"""{
         |    "using": [
         |        "urn:ietf:params:jmap:core",
         |        "com:linagora:params:jmap:contact:autocomplete"
         |    ],
         |    "methodCalls": [
         |        [
         |            "TMailContact/autocomplete",
         |            {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "filter": {
         |                    "text": "andre"
         |                }
         |            },
         |            "c1"
         |        ]
         |    ]
         |}""".stripMargin

    calmlyAwait.atMost(30, TimeUnit.SECONDS).untilAsserted { () =>
      val response: String = `given`
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
        .withOptions(new Options(IGNORING_ARRAY_ORDER))
        .isEqualTo(
          s"""{
             |    "sessionState": "${SESSION_STATE.value}",
             |    "methodResponses": [
             |        [
             |            "TMailContact/autocomplete",
             |            {
             |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
             |                "list": [
             |                    {
             |                        "id": "$${json-unit.ignore}",
             |                        "firstname": "$${json-unit.ignore}",
             |                        "surname": "$${json-unit.ignore}",
             |                        "emailAddress": "${ANDRE.asMailAddress().asString()}"
             |                    }
             |                ],
             |                "limit": 256
             |            },
             |            "c1"
             |        ]
             |    ]
             |}""".stripMargin)
    }
  }
}
