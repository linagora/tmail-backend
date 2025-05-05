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

import com.linagora.tmail.james.jmap.model.{MailboxClearRequest, MailboxClearResponse}
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, SetError, UnsignedInt}
import org.apache.james.jmap.mail.UnparsedMailboxId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsResult, JsValue, Json}

class MailboxClearSerializerTest {
  @Test
  def deserializeRequestShouldSucceed(): Unit = {
    val jsInput: JsValue = Json.parse(
      """{
        |  "accountId": "50fb9073ba109901291988b0d78e8a602a6fcd96fbde033eb46ca308779f8fac",
        |  "mailboxId": "mailboxId123"
        |}""".stripMargin)

    val deserializeResult: JsResult[MailboxClearRequest] = new MailboxClearSerializer().deserializeRequest(jsInput)

    assertThat(deserializeResult.isSuccess).isTrue
    assertThat(deserializeResult.get)
      .isEqualTo(MailboxClearRequest(
        accountId = AccountId.apply("50fb9073ba109901291988b0d78e8a602a6fcd96fbde033eb46ca308779f8fac"),
        mailboxId = UnparsedMailboxId.apply("mailboxId123")))
  }

  @Test
  def serializeSuccessResponseShouldSucceed(): Unit = {
    val response = MailboxClearResponse(
      accountId = AccountId.apply("50fb9073ba109901291988b0d78e8a602a6fcd96fbde033eb46ca308779f8fac"),
      totalDeletedMessagesCount = Some(UnsignedInt.liftOrThrow(10)))

    val serialized = new MailboxClearSerializer().serializeResponse(response)

    assertThat(serialized).isEqualTo(Json.parse(
      """{
        |    "accountId": "50fb9073ba109901291988b0d78e8a602a6fcd96fbde033eb46ca308779f8fac",
        |    "totalDeletedMessagesCount": 10
        |}""".stripMargin))
  }

  @Test
  def serializeFailureResponseShouldSucceed(): Unit = {
    val response = MailboxClearResponse(
      accountId = AccountId.apply("50fb9073ba109901291988b0d78e8a602a6fcd96fbde033eb46ca308779f8fac"),
      totalDeletedMessagesCount = None,
      notCleared = Some(SetError.serverFail(SetErrorDescription("An error occurred"))))

    val serialized = new MailboxClearSerializer().serializeResponse(response)

    assertThat(serialized).isEqualTo(Json.parse(
      """{
        |  "accountId": "50fb9073ba109901291988b0d78e8a602a6fcd96fbde033eb46ca308779f8fac",
        |  "notCleared": {
        |    "type": "serverFail",
        |    "description": "An error occurred"
        |  }
        |}""".stripMargin))
  }
}
