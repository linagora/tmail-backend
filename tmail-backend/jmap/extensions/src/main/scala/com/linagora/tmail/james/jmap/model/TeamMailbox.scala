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

package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.team.TeamMailbox
import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.method.WithAccountId

case class UnparsedTeamMailbox(value: String) {
  def parse(): Either[IllegalArgumentException, TeamMailbox] = TeamMailbox.fromString(value)
}

case class TeamMailboxRevokeAccessRequest(accountId: AccountId,
                                          ids: Option[Seq[UnparsedTeamMailbox]]) extends WithAccountId

case class TeamMailboxRevokeAccessResponse(accountId: AccountId,
                                           revoked: Option[Seq[TeamMailbox]],
                                           notRevoked: Option[Map[UnparsedTeamMailbox, SetError]])