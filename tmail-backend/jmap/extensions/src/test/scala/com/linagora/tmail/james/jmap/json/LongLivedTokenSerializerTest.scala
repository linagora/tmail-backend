package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.json.Fixture.ACCOUNT_ID
import com.linagora.tmail.james.jmap.json.LongLivedTokenSerializerTest.{LONG_LIVED_TOKEN_ID, LONG_LIVED_TOKEN_SECRET}
import com.linagora.tmail.james.jmap.longlivedtoken.{AuthenticationToken, LongLivedTokenId, LongLivedTokenSecret, UnparsedLongLivedTokenId}
import com.linagora.tmail.james.jmap.model.{LongLivedTokenCreationId, LongLivedTokenSetResponse, TokenCreationResult}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsValue, Json};

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
        |        "K38": {
        |            "deviceId": "My android device"
        |        },
        |        "K39": {
        |            "deviceId": "My android device 39"
        |        }
        |    }
        |}""".stripMargin)

    assertThat(LongLivedTokenSerializer.deserializeLongLivedTokenSetRequest(jsInput).isSuccess)
      .isTrue
  }

  @Test
  def serializeLongLiveTokenSetResponseShouldSuccess(): Unit = {
    val notCreated: Map[LongLivedTokenCreationId, SetError] = Map((LongLivedTokenCreationId("K39"),
      SetError(SetError.invalidArgumentValue, SetErrorDescription("des1"), None)))

    val created: Map[LongLivedTokenCreationId, TokenCreationResult] =
      Map(LongLivedTokenCreationId("K38") -> TokenCreationResult(LONG_LIVED_TOKEN_ID, AuthenticationToken(Username.of("bob"), LONG_LIVED_TOKEN_SECRET)))

    val notDestroyed: Map[UnparsedLongLivedTokenId, SetError] = Map((UnparsedLongLivedTokenId("K40"),
      SetError(SetError.invalidArgumentValue, SetErrorDescription("desc2"), None)))

    val destroyedId: LongLivedTokenId = LongLivedTokenId.generate
    val destroyed: Seq[LongLivedTokenId] = Seq(destroyedId)

    val longLivedTokenSetResponse: LongLivedTokenSetResponse = LongLivedTokenSetResponse(ACCOUNT_ID,
      Some(created),
      Some(destroyed),
      Some(notCreated),
      Some(notDestroyed))

    val actualValue: JsValue = LongLivedTokenSerializer.serializeLongLivedTokenSetResponse(longLivedTokenSetResponse)
    assertThat(Json.parse(
      s"""{
        |    "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
        |    "created": {
        |        "K38": {
        |            "id": "57b8ffe6-4587-4912-886a-6affc83efd90",
        |            "token": "bob_825eee6c-9488-442e-9079-19985c18c532"
        |        }
        |    },
        |    "destroyed": ["${destroyedId.value.toString}"],
        |    "notCreated": {
        |        "K39": {
        |            "type": "invalidArguments",
        |            "description": "des1"
        |        }
        |    },
        |    "notDestroyed": {
        |        "K40": {
        |            "type": "invalidArguments",
        |            "description": "desc2"
        |        }
        |    }
        |}""".stripMargin))
      .isEqualTo(actualValue)
  }

}
