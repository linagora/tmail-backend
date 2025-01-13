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

import java.util

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.common.LabelChangesMethodContract.firebasePushClient
import com.linagora.tmail.james.common.probe.JmapGuiceLabelProbe
import com.linagora.tmail.james.jmap.firebase.{FirebasePushClient, FirebasePushRequest}
import com.linagora.tmail.james.jmap.label.{LabelTypeName}
import com.linagora.tmail.james.jmap.model.{LabelChange, LabelId}
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.JsonMatchers
import net.javacrumbs.jsonunit.JsonMatchers.jsonEquals
import net.javacrumbs.jsonunit.core.Option
import org.apache.http.HttpStatus
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ACCOUNT_ID, ALICE_ACCOUNT_ID, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.jmap.rfc8621.contract.tags.CategoryTags
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.hamcrest.Matchers
import org.hamcrest.Matchers.{hasItem, hasKey, hasSize}
import org.junit.jupiter.api.{BeforeEach, Tag, Test}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, reset, times, verify, when}
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._

object LabelChangesMethodContract {
  val firebasePushClient: FirebasePushClient = mock(classOf[FirebasePushClient])
}

trait LabelChangesMethodContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString(), BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()

    reset(firebasePushClient)
    when(firebasePushClient.validateToken(any())).thenReturn(Mono.just(true))
    when(firebasePushClient.push(any(classOf[FirebasePushRequest]))).thenReturn(Mono.empty)
  }

  def stateFactory(): State.Factory

  @Test
  def labelChangesShouldReturnCorrectState(server: GuiceJamesServer): Unit = {
    val change: LabelChange = LabelChange(
      accountId = org.apache.james.jmap.api.model.AccountId.fromUsername(BOB),
      created = Set(LabelId.generate()),
      updated = Set(),
      destroyed = Set(),
      state = stateFactory().generate())
    server.getProbe(classOf[JmapGuiceLabelProbe])
      .saveLabelChange(change)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |	"Label/changes",
           |	{
           |		"accountId": "$ACCOUNT_ID",
           |		"oldState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |		"newState": "${change.state.getValue.toString}",
           |		"hasMoreChanges": false,
           |		"created": [ "${change.created.head.id.value}" ],
           |		"updated": [],
           |		"destroyed": []
           |	},
           |	"c1"
           |]""".stripMargin))
  }

  @Test
  @Tag(CategoryTags.BASIC_FEATURE)
  def shouldReturnCreatedChanges(): Unit = {
    // Given: use Label/set to create a label
    val createdLabelId: String = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{ "using": [ "urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "L13": {
           |            "displayName": "DisplayName1",
           |            "color": "#00ccdd"
           |          }
           |        }
           |      }, "c1"
           | ]]}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .body()
      .path("methodResponses[0][1].created.L13.id")

    // Then
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].created", hasItem(createdLabelId))
      .body("methodResponses[0][1].updated", hasSize(0))
      .body("methodResponses[0][1].destroyed", hasSize(0))
  }

  @Test
  def shouldReturnUpdatedChangesWhenRenameDisplayName(): Unit = {
    val labelId: String = createANewLabel()
    val newState = getNewState()
    updateLabel(labelId, "{ \"displayName\": \"newDisplayName2\"}")

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "$newState",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].updated", hasItem(labelId))
  }

  @Test
  def shouldReturnUpdatedChangesWhenRenameColor(): Unit = {
    val labelId: String = createANewLabel()
    val newState = getNewState()
    updateLabel(labelId, "{ \"color\": \"#00ccff\"}")

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "$newState",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].updated", hasItem(labelId))
  }

  @Test
  def shouldReturnDestroyedChangesWhenDestroyLabel(): Unit = {
    val labelId: String = createANewLabel()
    val newState = getNewState()
    removeLabel(labelId)

    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "$newState",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].destroyed", hasItem(labelId))
  }

  @Test
  def shouldFailWhenAccountIdNotFound(): Unit =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ALICE_ACCOUNT_ID",
           |      "sinceState": "${State.INITIAL.getValue}",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        """["error", {
          |    "type": "accountNotFound"
          |}, "c1"]""".stripMargin))

  @Test
  def shouldSupportMaxChangesQuery(): Unit = {
    val labelId1: String = createANewLabel("DisplayName1")
    val labelId2: String = createANewLabel("DisplayName2")
    val labelId3: String = createANewLabel("DisplayName3")
    val labelId4: String = createANewLabel("DisplayName4")
    val labelId5: String = createANewLabel("DisplayName5")
    val labelId6: String = createANewLabel("DisplayName6")
    val labelId7: String = createANewLabel("DisplayName7")

    val limit: Int = 5
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": $limit
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].created", jsonEquals(
        s"""[
           | "$labelId1",
           | "$labelId2",
           | "$labelId3",
           | "$labelId4",
           | "$labelId5"
           |]""".stripMargin).
        withOptions(ImmutableList.of(Option.IGNORING_ARRAY_ORDER)))
  }

  @Test
  def shouldReturnHasMoreChangeIsTrueWhenRemainElements(): Unit = {
    createANewLabel("DisplayName1")
    createANewLabel("DisplayName2")
    createANewLabel("DisplayName3")

    val limit: Int = 2
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": $limit
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].hasMoreChanges", Matchers.is(true))
  }

  @Test
  def shouldFailWhenSinceStateHasNotPermission(): Unit = {
    createANewLabel()
    val bobState: String = getNewState()

    `given`
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ANDRE_ACCOUNT_ID",
           |      "sinceState": "$bobState",
           |      "maxChanges": 5
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[ "error", {
           |    "type": "cannotCalculateChanges",
           |    "description": "State '$bobState' could not be found"
           |  }, "c1"]""".stripMargin))
  }

  @Test
  def shouldSupportDelegationWhenDelegatedUser(server: GuiceJamesServer): Unit = {
    val bobAccountId = ACCOUNT_ID

    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)
    val bobLabelId: String = createANewLabel()
    val bobState: String = getNewState()
    updateLabel(bobLabelId, "{ \"displayName\": \"newDisplayName2\"}")

    `given`
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$bobAccountId",
           |      "sinceState": "$bobState",
           |      "maxChanges": 5
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].updated", hasItem(bobLabelId))
  }

  @Test
  def shouldFailWhenSinceStateNotFound(): Unit = {
    val notFoundState: String = stateFactory().generate().getValue.toString
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "$notFoundState",
           |      "maxChanges": 5
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[ "error", {
           |    "type": "cannotCalculateChanges",
           |    "description": "State '$notFoundState' could not be found"
           |  }, "c1"]""".stripMargin))
  }

  @Test
  def shouldReturnUnknownMethodWhenOmittingItsCapability(): Unit =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""["error", {
           |    "type": "unknownMethod",
           |    "description": "Missing capability(ies): com:linagora:params:jmap:labels"
           |  }, "c1"]""".stripMargin))

  @Test
  def shouldReturnUnknownMethodWhenOmittingAllCapabilities(): Unit =
    `given`
      .body(
        s"""{
           |  "using": [],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""["error", {
           |    "type": "unknownMethod",
           |    "description": "Missing capability(ies): urn:ietf:params:jmap:core, com:linagora:params:jmap:labels"
           |  }, "c1"]""".stripMargin))

  @Test
  def shouldFailWhenInvalidSinceState(): Unit =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "invalidState",
           |      "maxChanges": 5
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0]", jsonEquals(
        s"""[
           |  "error", {
           |    "type": "invalidArguments",
           |    "description": "'/sinceState' property is not valid: error.expected.uuid"
           |  }, "c1"
           |]""".stripMargin))

  @Test
  def shouldFailWhenMaxChangesIsNegative(): Unit =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${State.INITIAL.getValue}",
           |      "maxChanges": -1
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(400)
      .contentType(JSON)
      .body("", jsonEquals(
        s"""{
           |    "type": "urn:ietf:params:jmap:error:notRequest",
           |    "status": 400,
           |    "detail": "The request was successfully parsed as JSON but did not match the type signature of the Request object: 'limit' needs to be strictly positive"
           |}""".stripMargin))

  @Test
  def shouldNotUpdateStateWhenCreationLabelFail(): Unit = {
    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |      "create": {
           |        "L13": {
           |          "displayName": "DisplayName1",
           |          "color": "#not_a_color"
           |        }
           |      }
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].notCreated.L13", Matchers.notNullValue())

  assertThat(getNewState())
      .isEqualTo(State.INITIAL.getValue.toString)
  }

  @Test
  def shouldNotUpdateStateWhenUpdateLabelFail(): Unit = {
    val labelId: String = createANewLabel()
    val stateAfterCreation = getNewState()

    `given`()
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |      "accountId": "$ACCOUNT_ID",
           |				"update": {
           |					"$labelId": {
           |						"color": "invalidColor"
           |					}
           |				}
           |    }, "c1"]
           |  ]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .contentType(JSON)
      .body(s"methodResponses[0][1].notUpdated.$labelId", Matchers.notNullValue())

    assertThat(getNewState())
      .isEqualTo(stateAfterCreation)
  }

  @Test
  def shouldNotUpdateStateWhenDestroyLabelFail(): Unit = {
    val labelId: String = createANewLabel()
    val stateAfterCreation = getNewState()

    `given`()
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |	"methodCalls": [
           |		["Label/set", {
           |			"accountId": "$ACCOUNT_ID",
           |			"destroy": ["@invalidId"]
           |		}, "c1"]
           |	]
           |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].type", Matchers.is("invalidArguments"))

    assertThat(getNewState())
      .isEqualTo(stateAfterCreation)
  }

  @Test
  def shouldPushToFCMWhenChangeByCreationRequest(): Unit = {
    registerFCMSubscribe()
    createANewLabel()
    val newState: String = getNewState()

    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient, times(1)).push(argumentCaptor.capture())
      assertThat(argumentCaptor.getValue.stateChangesMap())
        .isEqualTo(java.util.Map.of(s"$ACCOUNT_ID:${LabelTypeName.asString}", newState))
    })
  }

  @Test
  def shouldPushToFCMWhenChangeByUpdateRequest(): Unit = {
    registerFCMSubscribe()
    val labelId = createANewLabel()
    updateLabel(labelId, "{ \"color\": \"#00ccff\"}")
    val updateState: String = getNewState()

    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient, times(2)).push(argumentCaptor.capture())

      val stateChangesCapture: util.Map[String, String] = argumentCaptor.getAllValues.asScala
        .flatMap(_.stateChangesMap().asScala).toMap.asJava

      assertSoftly(softLy => {
        softLy.assertThat(stateChangesCapture).containsKey(s"$ACCOUNT_ID:${LabelTypeName.asString}")
        softLy.assertThat(stateChangesCapture).containsValue(updateState)
      })
    })
  }

  @Test
  def shouldPushToFCMWhenChangeByDestroyRequest(): Unit = {
    registerFCMSubscribe()
    val labelId = createANewLabel()
    removeLabel(labelId)
    val latestState: String = getNewState()

    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient, times(2)).push(argumentCaptor.capture())

      val stateChangesCapture: util.Map[String, String] = argumentCaptor.getAllValues.asScala
        .flatMap(_.stateChangesMap().asScala).toMap.asJava

      assertSoftly(softLy => {
        softLy.assertThat(stateChangesCapture).containsKey(s"$ACCOUNT_ID:${LabelTypeName.asString}")
        softLy.assertThat(stateChangesCapture).containsValue(latestState)
      })
    })
  }

  @Test
  def shouldPushToFCMOwnerWhenChangeStateByDelegatedUser(server: GuiceJamesServer) : Unit = {
    // bob register FCM
    registerFCMSubscribe()

    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val bobAccountId = ACCOUNT_ID

    // andre create bob's label
    `given`()
      .auth().basic(ANDRE.asString(), ANDRE_PASSWORD)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{ "using": [ "urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |        "accountId": "$bobAccountId",
           |        "create": {
           |          "L13": {
           |            "displayName": "newLabel2",
           |            "color": "#00ccdd"
           |          }
           |        }
           |      }, "c1"
           | ]]}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .body()
      .path("methodResponses[0][1].created.L13.id")

    val bobNewState: String = getNewState()
    awaitAtMostTenSeconds.untilAsserted(() => {
      val argumentCaptor: ArgumentCaptor[FirebasePushRequest] = ArgumentCaptor.forClass(classOf[FirebasePushRequest])
      verify(firebasePushClient, times(1)).push(argumentCaptor.capture())
      assertThat(argumentCaptor.getValue.stateChangesMap())
        .isEqualTo(java.util.Map.of(s"$bobAccountId:${LabelTypeName.asString}", bobNewState))
    })
  }

  private def registerFCMSubscribe(): Unit =
    `given`
      .body(
        s"""{
           |  "using": [
           |    "urn:ietf:params:jmap:core",
           |    "com:linagora:params:jmap:firebase:push"],
           |  "methodCalls": [[
           |        "FirebaseRegistration/set",
           |        {
           |            "create": {
           |                "4f29": {
           |                  "deviceClientId": "a889-ffea-910",
           |                  "token": "token1",
           |                  "types": ["Label"]
           |                }
           |              }
           |        },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .body("methodResponses[0][1].created", JsonMatchers.jsonNodePresent("4f29"))

  private def createANewLabel(displayName: String = "DisplayName1"): String =
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{ "using": [ "urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "create": {
           |          "L13": {
           |            "displayName": "$displayName",
           |            "color": "#00ccdd"
           |          }
           |        }
           |      }, "c1"
           | ]]}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .body()
      .path("methodResponses[0][1].created.L13.id")

  private def updateLabel(labelId: String, updatePathObject: String): Unit =
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{ "using": [ "urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "update": {
           |          "$labelId": $updatePathObject
           |        }
           |      }, "c1"
           | ]]}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .body("methodResponses[0][1].updated", hasKey(labelId))

  private def removeLabel(labelId: String): Unit =
    `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        s"""{ "using": [ "urn:ietf:params:jmap:core", "com:linagora:params:jmap:labels"],
           |  "methodCalls": [
           |    ["Label/set", {
           |        "accountId": "$ACCOUNT_ID",
           |        "destroy": [ "$labelId" ]
           |      }, "c1"
           | ]]}""".stripMargin)
    .when()
      .post()
    .`then`
      .log().ifValidationFails()
      .statusCode(HttpStatus.SC_OK)
      .body("methodResponses[0][1].destroyed", hasItem(labelId))

  private def getNewState(): String =
    `given`
      .body(
        s"""{
           |  "using": ["urn:ietf:params:jmap:core",
           |            "com:linagora:params:jmap:labels"],
           |  "methodCalls": [[
           |    "Label/changes",
           |    {
           |      "accountId": "$ACCOUNT_ID",
           |      "sinceState": "${SESSION_STATE.value}",
           |      "maxChanges": 50
           |    },
           |    "c1"]]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .path("methodResponses[0][1].newState")
}
