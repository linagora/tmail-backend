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

import com.linagora.tmail.james.jmap.json.Fixture.MAILBOX_ID_FACTORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import play.api.libs.json.{JsValue, Json}

class FilterSerializerTest {
  private def filterSetRequestAppendingIn(mailboxIds: String): JsValue = Json.parse(
    s"""{
       |  "accountId": "a",
       |  "update": {
       |    "singleton": [{
       |      "id": "1",
       |      "name": "r",
       |      "condition": {"field": "subject", "comparator": "contains", "value": "x"},
       |      "action": {"appendIn": {"mailboxIds": $mailboxIds}}
       |    }]
       |  }
       |}""".stripMargin)

  @Test
  def deserializeFilterSetRequestShouldSucceedForAValidMailboxId(): Unit =
    assertThat(FilterSerializer(MAILBOX_ID_FACTORY).deserializeFilterSetRequest(filterSetRequestAppendingIn("""["12"]""")).isSuccess)
      .isTrue

  @Test
  def deserializeFilterSetRequestShouldReturnJsErrorForAnEmptyMailboxId(): Unit =
    assertThat(FilterSerializer(MAILBOX_ID_FACTORY).deserializeFilterSetRequest(filterSetRequestAppendingIn("""["12", ""]""")).isError)
      .isTrue

  @Test
  def deserializeFilterSetRequestShouldReturnJsErrorForAnInvalidMailboxId(): Unit =
    assertThat(FilterSerializer(MAILBOX_ID_FACTORY).deserializeFilterSetRequest(filterSetRequestAppendingIn("""["not-a-mailbox-id"]""")).isError)
      .isTrue

  @Test
  def deserializeFilterSetRequestShouldReturnJsErrorForANonStringMailboxId(): Unit =
    assertThat(FilterSerializer(MAILBOX_ID_FACTORY).deserializeFilterSetRequest(filterSetRequestAppendingIn("[12]")).isError)
      .isTrue
}
