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

import com.linagora.tmail.james.jmap.json.{CalendarEventCounterSerializer => testee}
import com.linagora.tmail.james.jmap.model._
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, Id, SetError}
import org.apache.james.jmap.mail.BlobIds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class CalendarEventCounterSerializerTest {

  @Test
  def deserializeRequestShouldSuccess(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |        "blobIds": ["2c9f1b12-b35a-43e6-9af2-0106fb53a943"]
        |      }""".stripMargin)

    val deserializeResult: JsResult[CalendarEventCounterAcceptRequest] = testee.deserializeAcceptRequest(jsInput)

    assertThat(deserializeResult.isSuccess)
      .isTrue
    assertThat(deserializeResult.get)
      .usingRecursiveComparison()
      .isEqualTo(CalendarEventCounterAcceptRequest(
        accountId = AccountId.apply("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"),
        blobIds = BlobIds(Seq("2c9f1b12-b35a-43e6-9af2-0106fb53a943"))))
  }

  @Test
  def serializeAcceptedResponseShouldSuccess(): Unit = {
    val response: CalendarEventCounterAcceptedResponse = CalendarEventCounterAcceptedResponse(
      accountId = AccountId.from(Username.of("bob")).toOption.get,
      accepted = BlobIds(Seq("2c9f1b12-b35a-43e6-9af2-0106fb53a943")),
      notFound = Some(CalendarEventNotFound(Set(Id.validate("1111111-b35a-43e6-9af2-0106fb53a943").toOption.get))),
      notAccepted = Some(CalendarEventNotDone(Map(Id.validate("456").toOption.get -> SetError.invalidArguments(SetErrorDescription("Invalid ICS"))))))

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
          |    "notAccepted": {
          |        "456": {
          |            "type": "invalidArguments",
          |            "description": "Invalid ICS"
          |        }
          |    }
          |}""".stripMargin))
  }

}
