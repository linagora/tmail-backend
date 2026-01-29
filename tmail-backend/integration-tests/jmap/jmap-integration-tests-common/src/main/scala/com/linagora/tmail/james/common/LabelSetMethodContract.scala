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

import com.linagora.tmail.james.common.LabelSetMethodContract.{LABEL_COLOR, LABEL_NAME, LABEL_NEW_COLOR, LABEL_NEW_NAME, LABEL_DESCRIPTION, LABEL_NEW_DESCRIPTION}
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe
import com.linagora.tmail.james.jmap.model.{Label, LabelId}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.hamcrest.Matchers
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object LabelSetMethodContract {
  private val LABEL_NAME = "Important"
  private val LABEL_NEW_NAME = "New Important"
  private val LABEL_COLOR = "#00ccdd"
  private val LABEL_NEW_COLOR = "#00ccff"
  private val LABEL_DESCRIPTION = "This is an important label"
  private val LABEL_NEW_DESCRIPTION = "This is an new description for label"
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
  @Tag(CategoryTags.BASIC_FEATURE)
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
      softly.assertThat(label.description.get).isEqualTo(LABEL_DESCRIPTION)
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
         |          "displayName": "$LABEL_NAME",
         |          "description": "$LABEL_DESCRIPTION"
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
      softly.assertThat(label.description.get).isEqualTo(LABEL_DESCRIPTION)
      softly.assertThat(label.color.isEmpty).isEqualTo(true)
    })
  }

  @Test
  def labelSetCreateShouldSucceedWithMissingDescription(server: GuiceJamesServer): Unit = {
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
      softly.assertThat(label.description.isEmpty).isEqualTo(true)
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
           |          "description": "Missing '/displayName' property"
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
         |          "description": "$LABEL_DESCRIPTION",
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
         |          "description": "$LABEL_DESCRIPTION",
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
           |          "properties": ["keyword","id"]
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
         |          "color": "$LABEL_COLOR",
         |          "description": "$LABEL_DESCRIPTION"
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
      .whenIgnoringPaths("methodResponses[0][1].newState", "methodResponses[0][1].oldState", "methodResponses[1][1].state")
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
           |				"list": [{
           |						"id": "${label.id.id.value}",
           |						"displayName": "$LABEL_NAME",
           |						"keyword": "${label.keyword.flagName}",
           |						"color": "$LABEL_COLOR",
           |                        "description": "$LABEL_DESCRIPTION"
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
         |          "color": "#not_a_color",
         |          "description": "$LABEL_DESCRIPTION"
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
           |          "description": "'/color' property is not valid: The string should be a valid hexadecimal color value following this pattern #[a-fA-F0-9]{6}"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
  }

  @Test
  def labelSetDestroyShouldSucceed(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = "Label1", color = LABEL_COLOR, description = LABEL_DESCRIPTION)

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
    val bobLabelId: String = createLabel(accountId = bobAccountId, displayName = "Label1", color = LABEL_COLOR, description = LABEL_DESCRIPTION)

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
    val bobLabelId: String = createLabel(accountId = bobAccountId, displayName = "Label1", color = LABEL_COLOR, description = LABEL_DESCRIPTION)

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

  @Test
  def labelSetUpdateShouldFailWhenWrongProperties(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"4f29": {
         |						"wrongProperties": "Warning"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"notUpdated": {
           |					"4f29": {
           |						"type": "invalidArguments",
           |						"description": "Some unknown properties were specified",
           |						"properties": ["wrongProperties"]
           |					}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)
  }

  @Test
  def labelSetUpdateShouldSucceedWhenValidDisplayName(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"displayName": "$LABEL_NEW_NAME"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NEW_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_DESCRIPTION)
    })
  }

  @Test
  def labelSetUpdateShouldFailWhenInvalidDisplayName(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"4c29": {
         |						"displayName": 100
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"notUpdated": {
           |					"4c29": {
           |						"type": "invalidArguments",
           |						"description": "Expecting a JSON string as an argument",
           |						"properties": ["displayName"]
           |					}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)
  }

  @Test
  def labelSetUpdateShouldSucceedWhenValidColor(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"color": "$LABEL_NEW_COLOR"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_NEW_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_DESCRIPTION)
    })
  }

  @Test
  def labelSetUpdateShouldSucceedWhenValidDescription(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"description": "$LABEL_NEW_DESCRIPTION"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_NEW_DESCRIPTION)
    })
  }

  @Test
  def labelSetUpdateShouldSucceedWhenValidEmptyDescription(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"description": null
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_COLOR)
      softly.assertThat(updatedLabel.description.isEmpty).isTrue
    })
  }

  @Test
  def labelSetUpdateShouldFailWhenColorIsANumber(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"4c29": {
         |						"color": 100
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"notUpdated": {
           |					"4c29": {
           |						"type": "invalidArguments",
           |						"description": "Expecting a JSON string as an argument",
           |						"properties": ["color"]
           |					}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)
  }

  @Test
  def labelSetUpdateShouldFailWhenColorWrongPattern(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"4c29": {
         |						"color": "#not_a_color"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"notUpdated": {
           |					"4c29": {
           |						"type": "invalidArguments",
           |						"description": "The string should be a valid hexadecimal color value following this pattern #[a-fA-F0-9]{6}",
           |						"properties": ["color"]
           |					}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)
  }

  @Test
  def labelSetUpdateShouldSucceedWhenUpdateBothDisplayNameAndColor(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"displayName": "$LABEL_NEW_NAME",
         |						"color": "$LABEL_NEW_COLOR"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NEW_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_NEW_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_DESCRIPTION)
    })
  }

  @Test
  def labelSetUpdateShouldSucceedWhenUpdateDisplayNameAndColorAndDescription(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"displayName": "$LABEL_NEW_NAME",
         |						"color": "$LABEL_NEW_COLOR",
         |                      "description": "$LABEL_NEW_DESCRIPTION"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NEW_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_NEW_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_NEW_DESCRIPTION)
    })
  }

  @Test
  def labelSetUpdateShouldReturnNotFoundWhenNonExistingLabel(): Unit = {
    val randomLabelId: String = LabelId.generate().id.value

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$randomLabelId": {
         |						"displayName": "$LABEL_NEW_NAME",
         |						"color": "$LABEL_NEW_COLOR"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"notUpdated": {
           |					"$randomLabelId": {
           |						"type": "notFound",
           |						"description": null
           |					}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)
  }

  @Test
  def labelSetUpdateShouldSucceedWhenMixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val randomLabelId: String = LabelId.generate().id.value
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {
         |						"displayName": "$LABEL_NEW_NAME",
         |						"color": "$LABEL_NEW_COLOR",
         |                      "description": "$LABEL_NEW_DESCRIPTION"
         |					},
         |					"$randomLabelId": {
         |						"displayName": "$LABEL_NEW_NAME",
         |						"color": "$LABEL_NEW_COLOR",
         |                      "description": "$LABEL_NEW_DESCRIPTION"
         |					}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				},
           |				"notUpdated": {
           |					"$randomLabelId": {
           |						"type": "notFound",
           |						"description": null
           |					}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NEW_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_NEW_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_NEW_DESCRIPTION)
    })
  }

  @Test
  def labelSetUpdateShouldNoopWhenEmptyPatchObject(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |	"methodCalls": [[
         |			"Label/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"update": {
         |					"$createdLabelId": {}
         |				}
         |			}, "0"]]
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
           |	"methodResponses": [[
           |			"Label/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"updated": {
           |					"$createdLabelId": {}
           |				}
           |			},
           |			"0"]]
           |}""".stripMargin)

    SoftAssertions.assertSoftly(softly => {
      val updatedLabel: Label = server.getProbe(classOf[JmapGuiceLabelProbe]).listLabels(BOB).get(0)
      softly.assertThat(updatedLabel.displayName.value).isEqualTo(LABEL_NAME)
      softly.assertThat(updatedLabel.color.get.value).isEqualTo(LABEL_COLOR)
      softly.assertThat(updatedLabel.description.get).isEqualTo(LABEL_DESCRIPTION)
    })
  }

  @Test
  def newStateShouldBeUpToDate(): Unit = {
    val createdLabelId: String = createLabel(accountId = ACCOUNT_ID, displayName = LABEL_NAME, color = LABEL_COLOR, description = LABEL_DESCRIPTION)

    val request =
      s"""
         |{
         |   "using": [ "urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
         |   "methodCalls": [
         |       [
         |           "Label/set",
         |           {
         |                "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |                "update": {
         |                    "$createdLabelId": {
         |                      "displayName": "newName"
         |                    }
         |                }
         |           }, "c1"],
         |       [ "Label/changes",
         |       {
         |         "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |         "#sinceState": {
         |            "resultOf":"c1",
         |            "name":"Label/set",
         |            "path":"newState"
         |          }
         |       },
         |       "c2"]
         |   ]
         |}
         |""".stripMargin

    val response = `given`
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
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[1][1].oldState", "methodResponses[1][1].newState")
      .inPath("methodResponses[1][1]")
      .isEqualTo(
        s"""{
           |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |  "hasMoreChanges": false,
           |  "created": [],
           |  "updated": [],
           |  "destroyed": []
           |}""".stripMargin)
  }

  private def createLabel(accountId: String, displayName: String, color: String, description: String): String = {
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |	"methodCalls": [
           |		["Label/set", {
           |			"accountId": "$accountId",
           |			"create": {
           |				"L13": {
           |					"displayName": "$displayName",
           |					"color": "$color",
           |                    "description": "$description"
           |				}
           |			}
           |		}, "c1"]
           |	]
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
