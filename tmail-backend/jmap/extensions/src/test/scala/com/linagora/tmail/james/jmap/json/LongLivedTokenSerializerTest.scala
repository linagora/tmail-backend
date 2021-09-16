package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.json.Fixture.ACCOUNT_ID
import com.linagora.tmail.james.jmap.json.LongLivedTokenSerializerTest.{LONG_LIVED_TOKEN_ID, LONG_LIVED_TOKEN_SECRET}
import com.linagora.tmail.james.jmap.longlivedtoken.{DeviceId, LongLivedTokenId, LongLivedTokenSecret}
import com.linagora.tmail.james.jmap.model.{LongLivedTokenSetRequest, LongLivedTokenSetResponse, TokenCreateRequest, TokenCreateResponse}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json};

object LongLivedTokenSerializerTest {
  lazy val LONG_LIVED_TOKEN_ID: LongLivedTokenId = LongLivedTokenId.parse("57b8ffe6-4587-4912-886a-6affc83efd90").toOption.get
  lazy val LONG_LIVED_TOKEN_SECRET: LongLivedTokenSecret = LongLivedTokenSecret.parse("825eee6c-9488-442e-9079-19985c18c532").toOption.get
}

class LongLivedTokenSerializerTest {

  @Test
  def deserializeLongLivedTokenSetRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
        |    "create": {
        |        "deviceId": "My android device"
        |    }
        |}""".stripMargin)

    val jsResult: JsResult[LongLivedTokenSetRequest] = LongLivedTokenSerializer.deserializeLongLivedTokenSetRequest(jsInput)

    assertThat(jsResult.isSuccess)
      .isEqualTo(true)

    assertThat(jsResult.get)
      .isEqualTo(LongLivedTokenSetRequest(ACCOUNT_ID, TokenCreateRequest(DeviceId("My android device"))))
  }

  @Test
  def serializeLongLiveTokenSetResponseShouldSuccess(): Unit = {
    val longLivedTokenSetResponse: LongLivedTokenSetResponse = LongLivedTokenSetResponse(ACCOUNT_ID, TokenCreateResponse(LONG_LIVED_TOKEN_ID, LONG_LIVED_TOKEN_SECRET));

    val actualValue: JsValue = LongLivedTokenSerializer.serializeKeystoreGetResponse(longLivedTokenSetResponse)
    assertThat(Json.parse(
      """
        |{
        |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
        |    "created": {
        |        "id": "57b8ffe6-4587-4912-886a-6affc83efd90",
        |        "token": "825eee6c-9488-442e-9079-19985c18c532"
        |    }
        |}""".stripMargin))
      .isEqualTo(actualValue)
  }

}
