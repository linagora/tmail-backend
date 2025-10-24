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

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model._
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.{Id, Properties, SetError}
import org.apache.james.jmap.mail.UnparsedMailboxId
import org.apache.james.task.TaskId
import org.apache.james.task.TaskManager.Status
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class FolderFilteringActionSerializerTest {
  @Test
  def deserializeGetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "ids": ["2034-495-05857-57abcd-0876664"],
        |  "properties": ["status", "processedMessageCount", "successfulActions", "failedActions", "maximumAppliedActionReached", "mailboxId"]
        |}""".stripMargin)

    val deserializeResult: JsResult[FolderFilteringActionGetRequest] = FolderFilteringActionSerializer.deserializeGetRequest(jsInput)

    assertThat(deserializeResult.get.ids.list.head)
      .isEqualTo(UnparsedFolderFilteringActionId("2034-495-05857-57abcd-0876664"))
    assertThat(deserializeResult.get.properties.get.contains("status"))
      .isTrue
  }

  @Test
  def deserializeSetCreationRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "mailboxId": "123"
        |}""".stripMargin)

    val result: JsResult[FolderFilteringActionCreationRequest] = FolderFilteringActionSerializer.deserializeSetCreationRequest(jsInput)

    assertThat(result.isSuccess).isTrue
    assertThat(result.get).isEqualTo(FolderFilteringActionCreationRequest(UnparsedMailboxId.apply("123")))
  }

  @Test
  def deserializeSetRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "create": {
        |    "clientId1": { "mailboxId": "mb1" }
        |  }
        |}""".stripMargin)

    val result: JsResult[FolderFilteringActionSetRequest] = FolderFilteringActionSerializer.deserializeSetRequest(jsInput)

    assertThat(result.isSuccess).isTrue
    assertThat(result.get.create.get.head._1).isEqualTo(FolderFilteringActionCreationId("clientId1"))
  }

  @Test
  def serializeSetResponseShouldSucceed(): Unit = {
    val notCreated: Map[FolderFilteringActionCreationId, SetError] = Map(
      (FolderFilteringActionCreationId("K39"), SetError(
        SetError.invalidArgumentValue,
        SetError.SetErrorDescription("des1"), None)))

    val created: Map[FolderFilteringActionCreationId, FolderFilteringActionCreationResponse] = Map(
      FolderFilteringActionCreationId("K38") -> FolderFilteringActionCreationResponse(TaskId.fromString("4bf6d081-aa30-11e9-bf6c-2d3b9e84aafd")))

    val response: FolderFilteringActionSetResponse = FolderFilteringActionSetResponse(created = Some(created), notCreated = Some(notCreated))

    assertThat(FolderFilteringActionSerializer.serializeSetResponse(response))
      .isEqualTo(Json.parse(
        """{
          |  "created": {
          |    "K38": {
          |      "id": "4bf6d081-aa30-11e9-bf6c-2d3b9e84aafd"
          |    }
          |  },
          |  "notCreated": {
          |    "K39": {
          |      "type": "invalidArguments",
          |      "description": "des1"
          |    }
          |  }
          |}""".stripMargin))
  }

  @Test
  def serializeGetResponseShouldSucceed(): Unit = {
    val list: Seq[FolderFilteringAction] = Seq(FolderFilteringAction(
      id = TaskId.fromString("77731634-ea82-4a1a-bd4c-9f8ece4f66c7"),
      status = Status.IN_PROGRESS,
      processedMessageCount = ProcessedMessageCount(99L),
      successfulActions = SuccessfulActions(12L),
      failedActions = FailedActions(3L),
      maximumAppliedActionReached = true))

    val notFound: Set[UnparsedFolderFilteringActionId] = Set(UnparsedFolderFilteringActionId(Id.validate("whatever").toOption.get))
    val response: FolderFilteringActionGetResponse = FolderFilteringActionGetResponse(list, notFound)

    assertThat(FolderFilteringActionSerializer.serializeGetResponse(response, FolderFilteringActionGetRequest.allSupportedProperties))
      .isEqualTo(Json.parse(
        """{
          |  "list": [{
          |    "id": "77731634-ea82-4a1a-bd4c-9f8ece4f66c7",
          |    "status": "inProgress",
          |    "processedMessageCount": 99,
          |    "successfulActions": 12,
          |    "failedActions": 3,
          |    "maximumAppliedActionReached": true
          |  }],
          |  "notFound": ["whatever"]
          |}""".stripMargin))
  }

  @Test
  def serializeGetResponseShouldFilterByProperties(): Unit = {
    val list: Seq[FolderFilteringAction] = Seq(FolderFilteringAction(
      id = TaskId.fromString("77731634-ea82-4a1a-bd4c-9f8ece4f66c7"),
      status = Status.IN_PROGRESS,
      processedMessageCount = ProcessedMessageCount(99L),
      successfulActions = SuccessfulActions(12L),
      failedActions = FailedActions(3L),
      maximumAppliedActionReached = false))

    val notFound: Set[UnparsedFolderFilteringActionId] = Set(UnparsedFolderFilteringActionId(Id.validate("whatever").toOption.get))
    val response: FolderFilteringActionGetResponse = FolderFilteringActionGetResponse(list, notFound)

    assertThat(FolderFilteringActionSerializer.serializeGetResponse(response, Properties("id", "status")))
      .isEqualTo(Json.parse(
        """{
          |  "list": [{
          |    "id": "77731634-ea82-4a1a-bd4c-9f8ece4f66c7",
          |    "status": "inProgress"
          |  }],
          |  "notFound": ["whatever"]
          |}""".stripMargin))
  }
}
