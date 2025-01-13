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

import com.linagora.tmail.team.TeamMemberRole
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.method.WithAccountId

case class TeamMailboxMemberGetRequest(accountId: AccountId,
                                       ids: Option[Set[String]] = None) extends WithAccountId

case class TeamMailboxMemberGetResponse(accountId: AccountId,
                                        list: Seq[TeamMailboxMemberDTO],
                                        notFound: Seq[String])

case class TeamMailboxMemberDTO(id: String,
                                members: Map[String, TeamMailboxMemberRoleDTO] = Map.empty)

case class TeamMailboxMemberRoleDTO(role: String) {
  def validate: Either[InvalidRoleException, TeamMemberRole] = TeamMemberRole.from(role) match {
    case None => Left(InvalidRoleException(this))
    case Some(r) => Right(r)
  }
}