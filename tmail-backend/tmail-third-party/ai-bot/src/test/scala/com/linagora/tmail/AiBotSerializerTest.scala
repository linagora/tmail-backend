/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * ****************************************************************** */
package com.linagora.tmail

import com.linagora.tmail.jmap.aibot.json.AiBotSerializer
import com.linagora.tmail.jmap.aibot.{AiBotSuggestReplyRequest, AiBotSuggestReplyResponse}
import org.apache.james.jmap.core.{AccountId, Id}
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import org.scalatest.matchers.should.Matchers

class AiBotSerializerTest extends AnyWordSpec with Matchers {

  "AiBotDeSerializer" should {
    "deserialize AiBotSuggestReplyRequest from valid JSON" in {
      val jsonString =
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "emailId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "userInput": "Hello AI!"
          |}
          |""".stripMargin

      val expected = AiBotSuggestReplyRequest(
        accountId = AccountId(Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get),
        emailId = Some(Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get),
        userInput = "Hello AI!"
      )
      val result = AiBotSerializer.deserializeRequest(Json.parse(jsonString))
      result should equal(JsSuccess(expected))
    }
  }

  "AiBotNullRequestDeSerializer" should {
    "deserialize AiBotSuggestReplyRequest from valid JSON with null mailId" in {
      val jsonString =
        """
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "userInput": "Hello AI!"
          |}
          |""".stripMargin

      val expected = AiBotSuggestReplyRequest(
        accountId = AccountId(Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get),
        emailId = None,
        userInput = "Hello AI!"
      )
      val result = AiBotSerializer.deserializeRequest(Json.parse(jsonString))
      result should equal(JsSuccess(expected))
    }
  }

  "AiBotSerializer" should {
    "deserialize AiBotSuggestReplyRequest from valid JSON" in {
      val expected = Json.parse ("""
          |{
          |  "accountId": "aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8",
          |  "suggestion": "this is an email suggestion"
          |}
          |""".stripMargin)

      val actual = AiBotSuggestReplyResponse.from(
        accountId = AccountId(Id.validate("aHR0cHM6Ly93d3cuYmFzZTY0ZW5jb2RlLm9yZy8").toOption.get),
        results = "this is an email suggestion"
      )
      val result = AiBotSerializer.serializeResponse(actual)
      print(result)
      result should equal(expected)
    }
  }
}