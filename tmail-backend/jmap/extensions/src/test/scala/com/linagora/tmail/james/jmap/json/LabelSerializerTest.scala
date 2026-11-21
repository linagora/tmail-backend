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

import com.linagora.tmail.james.jmap.label.LabelRepositoryContract.RED
import com.linagora.tmail.james.jmap.model.{DisplayName, LabelCreationRequest, LabelDescription, LabelPatchObject, LabelPatchUpdateValidationException}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsError, JsResult, JsString, Json}

class LabelSerializerTest {

  @Test
  def deserializeCreationRequestShouldSuccess(): Unit = {
    val jsInput = Json.parse(
      """
        {
         "displayName":"Label 1",
         "color":"#FF0000"
        }
        """)

    val deserializeResult: JsResult[LabelCreationRequest] = LabelSerializer.deserializeLabelCreationRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .usingRecursiveComparison()
      .isEqualTo(LabelCreationRequest(
        displayName = DisplayName("Label 1"),
        color = Some(RED),
        description = None
      ))
  }

  @Test
  def givenObjectContainsUnknownPropertyDeserializeCreationRequestShouldFail(): Unit = {
    val jsInput = Json.parse(
      """
        {
         "displayName":"Label 1",
         "color":"#FF0000",
         "unknown": "BAD"
        }
        """)

    val deserializeResult: JsResult[LabelCreationRequest] = LabelSerializer.deserializeLabelCreationRequest(jsInput)

    assertThat(deserializeResult.isError)
      .isTrue
  }

  @Test
  def givenObjectContainsServerPropertyDeserializeCreationRequestShouldFail(): Unit = {
    val jsInput = Json.parse(
      """
        {
         "displayName":"Label 1",
         "color":"#FF0000",
         "id": "BAD"
        }
        """)

    val deserializeResult: JsResult[LabelCreationRequest] = LabelSerializer.deserializeLabelCreationRequest(jsInput)

    assertThat(deserializeResult.isError)
      .isTrue
  }

  private def creationErrorMessage(deserializeResult: JsResult[LabelCreationRequest]): String =
    com.linagora.tmail.james.jmap.method.standardErrorMessage(deserializeResult.asInstanceOf[JsError].errors)

  @Test
  def deserializeCreationRequestShouldRejectTooLongDisplayName(): Unit = {
    val jsInput = Json.obj("displayName" -> "x".repeat(DisplayName.MAX_LENGTH + 1))

    val deserializeResult: JsResult[LabelCreationRequest] = LabelSerializer.deserializeLabelCreationRequest(jsInput)

    assertThat(creationErrorMessage(deserializeResult))
      .isEqualTo("'/displayName' property is not valid: 'displayName' must not be longer than 255 characters")
  }

  @Test
  def deserializeCreationRequestShouldRejectBlankDisplayName(): Unit = {
    val jsInput = Json.obj("displayName" -> " ")

    assertThat(LabelSerializer.deserializeLabelCreationRequest(jsInput).isError).isTrue
  }

  @Test
  def deserializeCreationRequestShouldRejectTooLongDescription(): Unit = {
    val jsInput = Json.obj("displayName" -> "Label 1", "description" -> "x".repeat(LabelDescription.MAX_LENGTH + 1))

    val deserializeResult: JsResult[LabelCreationRequest] = LabelSerializer.deserializeLabelCreationRequest(jsInput)

    assertThat(creationErrorMessage(deserializeResult))
      .isEqualTo("'/description' property is not valid: 'description' must not be longer than 4096 characters")
  }

  @Test
  def deserializeCreationRequestShouldAcceptMaximumLengths(): Unit = {
    val jsInput = Json.obj("displayName" -> "x".repeat(DisplayName.MAX_LENGTH), "description" -> "y".repeat(LabelDescription.MAX_LENGTH))

    assertThat(LabelSerializer.deserializeLabelCreationRequest(jsInput).isSuccess).isTrue
  }

  @Test
  def patchShouldRejectTooLongDisplayName(): Unit =
    assertThat(LabelPatchObject(Map("displayName" -> JsString("x".repeat(DisplayName.MAX_LENGTH + 1)))).validate().left.toOption.get)
      .isEqualTo(LabelPatchUpdateValidationException("'displayName' must not be longer than 255 characters", Some("displayName")))

  @Test
  def patchShouldRejectBlankDisplayName(): Unit =
    assertThat(LabelPatchObject(Map("displayName" -> JsString(""))).validate().left.toOption.get)
      .isEqualTo(LabelPatchUpdateValidationException("'displayName' is required and must not be empty", Some("displayName")))

  @Test
  def patchShouldRejectTooLongDescription(): Unit =
    assertThat(LabelPatchObject(Map("description" -> JsString("x".repeat(LabelDescription.MAX_LENGTH + 1)))).validate().left.toOption.get)
      .isEqualTo(LabelPatchUpdateValidationException("'description' must not be longer than 4096 characters", Some("description")))
}
