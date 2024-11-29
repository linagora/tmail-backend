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
        color = Some(RED)
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
