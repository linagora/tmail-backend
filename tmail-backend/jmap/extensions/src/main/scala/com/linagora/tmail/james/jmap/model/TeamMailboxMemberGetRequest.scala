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