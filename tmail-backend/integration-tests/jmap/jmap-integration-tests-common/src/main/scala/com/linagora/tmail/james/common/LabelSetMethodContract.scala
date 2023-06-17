package com.linagora.tmail.james.common

import com.linagora.tmail.james.common.LabelSetMethodContract.{LABEL_COLOR, LABEL_NAME}
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe
import com.linagora.tmail.james.jmap.model.{Label, LabelId}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Test}

object LabelSetMethodContract {
  private val LABEL_NAME = "Important"
  private val LABEL_COLOR = "#00ccdd"
}

trait LabelSetMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build()
  }

  @Test
  def labelSetCreateShouldSucceedWithValidData(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val label: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .listLabels(BOB)
      .get(0)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "created": {
           |        "L13": {
           |          "id": "${label.id.id.value}",
           |          "keyword": "${label.keyword.flagName}"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateShouldStoreDataCorrectly(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val label: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .listLabels(BOB)
      .get(0)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(label.displayName.value).isEqualTo(LABEL_NAME)
      softly.assertThat(label.color.get.value).isEqualTo(LABEL_COLOR)
    })
  }

  @Test
  def labelSetCreateShouldSucceedWithMissingColor(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val label: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .listLabels(BOB)
      .get(0)

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(label.displayName.value).isEqualTo(LABEL_NAME)
      softly.assertThat(label.color.isEmpty).isEqualTo(true)
    })
  }

  @Test
  def labelSetCreateShouldReturnUnknownMethodWhenOmittingItsCapability(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["error", {
           |      "type": "unknownMethod",
           |      "description": "Missing capability(ies): com:linagora:params:jmap:labels"
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateShouldReturnUnknownMethodWhenOmittingAllCapabilities(): Unit = {
    val request =
      s"""{
         |  "using": [],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["error", {
           |      "type": "unknownMethod",
           |      "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:labels"
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateWithWrongAccountIdShouldFail(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ALICE_ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
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
  def labelSetCreateShouldReturnNotCreatedWhenMissingDisplayName(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "notCreated": {
           |        "L13": {
           |          "type": "invalidArguments",
           |          "description": "Missing '/displayName' property in Label object"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateShouldReturnNotCreatedWhenUnknownParam(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR",
         |          "unknown": "blabla"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "notCreated": {
           |        "L13": {
           |          "type": "invalidArguments",
           |          "description": "Some unknown properties were specified",
           |          "properties": ["unknown"]
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateShouldReturnNotCreatedWhenServerSetParam(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR",
         |          "id": "123",
         |          "keyword": "keyword"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "notCreated": {
           |        "L13": {
           |          "type": "invalidArguments",
           |          "description": "Some server-set properties were specified",
           |          "properties": ["id", "keyword"]
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateShouldSucceedWithDelegatedAccount(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`(baseRequestSpecBuilder(server)
        .setAuth(authScheme(UserCredential(ANDRE, ANDRE_PASSWORD)))
        .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .build)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val label: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .listLabels(BOB)
      .get(0)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "created": {
           |        "L13": {
           |          "id": "${label.id.id.value}",
           |          "keyword": "${label.keyword.flagName}"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateWithBackReferenceShouldSucceed(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "$LABEL_COLOR"
         |        }
         |      }
         |    }, "c1"],
         |    ["Label/get", {
         |			"accountId": "$ACCOUNT_ID",
         |			"ids": ["#L13"]
         |		}, "c2"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    val label: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .listLabels(BOB)
      .get(0)

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "created": {
           |        "L13": {
           |          "id": "${label.id.id.value}",
           |          "keyword": "${label.keyword.flagName}"
           |        }
           |      }
           |    }, "c1"],
           |    [
           |			"Label/get",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"notFound": [],
           |				"state": "${INSTANCE.value}",
           |				"list": [{
           |						"id": "${label.id.id.value}",
           |						"displayName": "$LABEL_NAME",
           |						"keyword": "${label.keyword.flagName}",
           |						"color": "$LABEL_COLOR"
           |					}
           |				]
           |			},
           |			"c2"
           |		]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetCreateShouldReturnNotCreatedWhenColorWrongPattern(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |  "methodCalls": [
         |    ["Label/set", {
         |      "accountId": "$ACCOUNT_ID",
         |      "create": {
         |        "L13": {
         |          "displayName": "$LABEL_NAME",
         |          "color": "#not_a_color"
         |        }
         |      }
         |    }, "c1"]
         |  ]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |  "sessionState": "${SESSION_STATE.value}",
           |  "methodResponses": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "notCreated": {
           |        "L13": {
           |          "type": "invalidArguments",
           |          "description": "'/color' property in Label object is not valid: The string should be a valid hexadecimal color value following this pattern #[a-fA-F0-9]{6}"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetDestroyShouldSucceed(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = "Label1")

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [
         |		["Label/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"destroy": ["$createdLabelId"]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Label/set",
           |			{
           |				"accountId": "$ACCOUNT_ID",
           |				"destroyed": ["$createdLabelId"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB))
      .isEmpty()
  }

  @Test
  def labelSetDestroyShouldFailWhenInvalidId(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [
         |		["Label/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"destroy": ["@invalidId"]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].type", Matchers.is("invalidArguments"))
      .body("methodResponses[0][1].description", Matchers.containsString("contains some invalid characters. Should be [#a-zA-Z0-9-_]"))
  }

  @Test
  def labelSetDestroyShouldBeIdempotent(): Unit = {
    val randomLabelId: String = LabelId.generate().id.value

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [
         |		["Label/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"destroy": ["$randomLabelId"]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Label/set",
           |			{
           |				"accountId": "$ACCOUNT_ID",
           |				"destroyed": ["$randomLabelId"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def labelSetDestroyShouldSupportDelegationWhenDelegatedUser(server: GuiceJamesServer): Unit = {
    val bobAccountId = ACCOUNT_ID
    val bobLabelId: String = createLabel(accountId = bobAccountId, displayName = "Label1")

    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [
         |		["Label/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"destroy": ["$bobLabelId"]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response = `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState")
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Label/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"destroyed": ["$bobLabelId"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB))
      .isEmpty()
  }

  @Test
  def labelSetDestroyShouldNotSupportDelegationWhenNotDelegatedUser(server: GuiceJamesServer): Unit = {
    val bobAccountId = ACCOUNT_ID
    val bobLabelId: String = createLabel(accountId = bobAccountId, displayName = "Label1")

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [
         |		["Label/set", {
         |			"accountId": "$ACCOUNT_ID",
         |			"destroy": ["$bobLabelId"]
         |		}, "c1"]
         |	]
         |}""".stripMargin

    val response = `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)

    assertThat(server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB))
      .hasSize(1)
  }

  private def createLabel(accountId: String, displayName: String): String = {
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |      "accountId": "$accountId",
           |      "create": {
           |        "L13": {
           |          "displayName": "$displayName"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].created.L13.id")
      .asInstanceOf[String]
  }
}
