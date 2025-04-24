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
package com.linagora.tmail.jmap.mail;

import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.method.WithAccountId

import java.util.Optional

case class AiBotSuggestReplyRequest(
                                     accountId: AccountId,
                                     emailId: Option[Id],
                                     userInput: String
                                   )extends WithAccountId

object AiBotSuggestReplyResponse {
  def from(accountId: AccountId, results: String): AiBotSuggestReplyResponse =
    AiBotSuggestReplyResponse(accountId, results)
}

case class AiBotSuggestReplyResponse(
                                      accountId: AccountId,
                                      suggestion: String
                                    )extends WithAccountId
