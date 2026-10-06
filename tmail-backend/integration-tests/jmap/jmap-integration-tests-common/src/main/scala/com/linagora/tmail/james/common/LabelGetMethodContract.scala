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
import com.linagora.tmail.james.common.LabelGetMethodContract.{BLUE, RED}
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationRequest, LabelId}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.Username
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE_PASSWORD, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.hamcrest.Matchers.hasKey
import org.junit.jupiter.api.{BeforeEach, Tag, Test}

object LabelGetMethodContract {
  case class TestContext(bobUsername: Username, andreUsername: Username) {
    val bobAccountId: String = accountId(bobUsername)
  }

  private val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  val RED: Color = Color("#FF0000")
  val BLUE: Color = Color("#0000FF")
}

trait LabelGetMethodContract {
  def bobUsername: Username = LabelGetMethodContract.currentContext.get().bobUsername

  def bobAccountId: String = LabelGetMethodContract.currentContext.get().bobAccountId

  def andreUsername: Username = LabelGetMethodContract.currentContext.get().andreUsername

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val andre = Username.fromLocalPartWithDomain(s"andre$uniqueSuffix", DOMAIN)
    LabelGetMethodContract.currentContext.set(LabelGetMethodContract.TestContext(bob, andre))

    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(bobUsername.asString(), BOB_PASSWORD)
      .addUser(andreUsername.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def shouldReturnLabelCapabilityInSessionRoute(): Unit = {
    val response = `given`()
      .when()
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("capabilities", hasKey("com:linagora:params:jmap:labels"))
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("capabilities.com:linagora:params:jmap:labels")
      .isEqualTo("""{"version": 2}""")
  }

  @Test
  def missingLabelCapabilityShouldFail(): Unit = {
    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": null
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"error",
         |			{
         |				"type": "unknownMethod",
         |				"description": "Missing capability(ies): com:linagora:params:jmap:labels"
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def getShouldReturnEmptyLabelsByDefault(): Unit = {
    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": null
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"state": "${INSTANCE.value}",
         |				"list": [],
         |				"notFound": []
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def fetchNullIdsShouldReturnAllLabels(server: GuiceJamesServer): Unit = {
    val label1: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .addLabel(bobUsername, LabelCreationRequest(DisplayName("Label 1"), Some(RED), Some("Description for label 1")))
    val label2: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .addLabel(bobUsername, LabelCreationRequest(DisplayName("Label 2"), Some(BLUE), Some("Description for label 2")))

    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": null
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
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"notFound": [],
         |				"state": "${INSTANCE.value}",
         |				"list": [{
         |						"id": "${label1.id.id.value}",
         |						"displayName": "Label 1",
         |						"keyword": "${label1.keyword.flagName}",
         |						"color": "${RED.value}",
         |            "description": "Description for label 1",
         |            "readOnly": false
         |					},
         |					{
         |						"id": "${label2.id.id.value}",
         |						"displayName": "Label 2",
         |						"keyword": "${label2.keyword.flagName}",
         |						"color": "${BLUE.value}",
         |            "description": "Description for label 2",
         |            "readOnly": false
         |					}
         |				]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def notFoundCase(): Unit = {
    val randomLabelId: String = LabelId.generate().id.value

    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": ["$randomLabelId", "notFound"]
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
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
      s"""{
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"notFound": ["$randomLabelId", "notFound"],
         |				"state": "${INSTANCE.value}",
         |				"list": []
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def mixedFoundAndNotFoundCase(server: GuiceJamesServer): Unit = {
    val createdLabelId: String = server.getProbe(classOf[JmapGuiceLabelProbe])
      .addLabel(bobUsername, LabelCreationRequest(DisplayName("Label 1"), Some(RED), Some("Description for label 1")))
      .id.id.value
    val randomLabelId: String = LabelId.generate().id.value

    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": ["$randomLabelId", "$createdLabelId"]
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"notFound": ["$randomLabelId"],
         |				"state": "${INSTANCE.value}",
         |				"list": [{
         |						"id": "$${json-unit.ignore}",
         |						"displayName": "Label 1",
         |						"keyword": "$${json-unit.ignore}",
         |						"color": "${RED.value}",
         |                      "description": "Description for label 1",
         |                      "readOnly": false
         |					}]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldReturnAllPropertiesByDefault(server: GuiceJamesServer): Unit = {
    val label1: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .addLabel(bobUsername, LabelCreationRequest(DisplayName("Label 1"), Some(RED), Some("Description for label 1")))

    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": null
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"notFound": [],
         |				"state": "${INSTANCE.value}",
         |				"list": [{
         |						"id": "${label1.id.id.value}",
         |						"displayName": "Label 1",
         |						"keyword": "${label1.keyword.flagName}",
         |						"color": "${RED.value}",
         |            "description": "Description for label 1",
         |            "readOnly": false
         |					}]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def invalidPropertiesShouldFail(): Unit = {
    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": null,
               |				"properties": ["invalid"]
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"error",
         |			{
         |				"type": "invalidArguments",
         |				"description": "The following properties [invalid] do not exist."
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldSupportFilteringByProperties(server: GuiceJamesServer): Unit = {
    val label1: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .addLabel(bobUsername, LabelCreationRequest(DisplayName("Label 1"), Some(RED), Some("Description for label 1")))

    val response = `given`
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "${bobAccountId}",
               |				"ids": null,
               |				"properties": ["keyword"]
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"notFound": [],
         |				"state": "${INSTANCE.value}",
         |				"list": [{
         |						"id": "${label1.id.id.value}",
         |						"keyword": "${label1.keyword.flagName}"
         |					}]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldSupportDelegation(server: GuiceJamesServer): Unit = {
    val label1: Label = server.getProbe(classOf[JmapGuiceLabelProbe])
      .addLabel(bobUsername, LabelCreationRequest(DisplayName("Label 1"), Some(RED), Some("Description for label 1")))

    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(bobUsername, andreUsername)

    val response = `given`
      .auth().basic(andreUsername.asString(), ANDRE_PASSWORD)
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "$bobAccountId",
               |				"ids": null
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
         |	"sessionState": "${SESSION_STATE.value}",
         |	"methodResponses": [
         |		[
         |			"Label/get",
         |			{
         |				"accountId": "${bobAccountId}",
         |				"notFound": [],
         |				"state": "${INSTANCE.value}",
         |				"list": [{
         |						"id": "${label1.id.id.value}",
         |						"displayName": "Label 1",
         |						"keyword": "${label1.keyword.flagName}",
         |						"color": "${RED.value}",
         |            "description": "Description for label 1",
         |            "readOnly": false
         |					}]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin)
  }

  @Test
  def shouldFailWhenNotDelegated(): Unit = {
    val response = `given`
      .auth().basic(andreUsername.asString(), ANDRE_PASSWORD)
      .body(s"""{
               |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
               |	"methodCalls": [
               |		[
               |			"Label/get",
               |			{
               |				"accountId": "$bobAccountId",
               |				"ids": null
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
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
           |	"type": "accountNotFound"
           |}""".stripMargin)
  }

  @Test
  def labelGetShouldReturnLatestState(): Unit = {
    createLabel(accountId = bobAccountId, displayName = "LABEL_NAME", color = RED.value, description = "LABEL_DESCRIPTION")

    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |	"methodCalls": [
           |		[
           |			"Label/get",
           |			{
           |				"accountId": "${bobAccountId}",
           |				"ids": null
           |			},
           |			"c1"
           |		],
           |		[
           |			"Label/changes",
           |			{
           |				"accountId": "${bobAccountId}",
           |				"#sinceState": {
           |					"resultOf": "c1",
           |					"name": "Label/get",
           |					"path": "state"
           |				}
           |			},
           |			"c2"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .withOptions(Option.IGNORING_ARRAY_ORDER)
      .whenIgnoringPaths("methodResponses[1][1].oldState", "methodResponses[1][1].newState")
      .inPath("methodResponses[1][1]")
      .isEqualTo(
        s"""{
           |  "accountId": "${bobAccountId}",
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
