package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.json.{CalendarEventReplySerializer => testee}
import com.linagora.tmail.james.jmap.model.{CalendarEventNotDone, CalendarEventNotFound, CalendarEventReplyAcceptedResponse, CalendarEventReplyRequest, LanguageLocation}
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.{AccountId, Id}
import org.apache.james.jmap.mail.BlobIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class CalendarEventReplySerializerTest {

  @Test
  def deserializeRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "blobIds": ["2c9f1b12-b35a-43e6-9af2-0106fb53a943"],
        |        "language": "fr"
        |      }""".stripMargin)

    val deserializeResult: JsResult[CalendarEventReplyRequest] = testee.deserializeRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .usingRecursiveComparison()
      .isEqualTo(CalendarEventReplyRequest(
        accountId = AccountId.apply("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
        blobIds = BlobIds(Seq("2c9f1b12-b35a-43e6-9af2-0106fb53a943")),
        language = Some(LanguageLocation.fromString("fr").toOption.get)))
  }

  @Test
  def serializeAcceptedResponseShouldSuccess(): Unit = {
    val response: CalendarEventReplyAcceptedResponse = CalendarEventReplyAcceptedResponse(
      accountId = AccountId.from(Username.of("bob")).toOption.get,
      accepted = BlobIds(Seq("2c9f1b12-b35a-43e6-9af2-0106fb53a943")),
      notFound = Some(CalendarEventNotFound(Set(Id.validate("1111111-b35a-43e6-9af2-0106fb53a943").toOption.get))),
      notAccepted = Some(CalendarEventNotDone(Set(Id.validate("456").toOption.get)))
    )

    assertThat(testee.serialize(response))
      .isEqualTo(Json.parse(
        """{
          |    "accountId": "81b637d8fcd2c6da6359e6963113a1170de795e4b725b84d1e0b4cfd9ec58ce9",
          |    "accepted": [
          |        "2c9f1b12-b35a-43e6-9af2-0106fb53a943"
          |    ],
          |    "notFound": [
          |        "1111111-b35a-43e6-9af2-0106fb53a943"
          |    ],
          |    "notAccepted": [
          |        "456"
          |    ]
          |}""".stripMargin))
  }

}
