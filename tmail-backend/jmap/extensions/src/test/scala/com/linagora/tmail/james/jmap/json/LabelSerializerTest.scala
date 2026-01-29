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
import com.linagora.tmail.james.jmap.model.{DisplayName, LabelCreationRequest}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, Json}

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
}
