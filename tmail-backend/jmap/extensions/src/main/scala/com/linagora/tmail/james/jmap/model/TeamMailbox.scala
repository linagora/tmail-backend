package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.team.TeamMailbox
import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.method.WithAccountId

import scala.collection.Seq

case class UnparsedTeamMailbox(value: String) {
  def parse(): Either[IllegalArgumentException, TeamMailbox] = TeamMailbox.fromString(value)
}

case class TeamMailboxRevokeAccessRequest(accountId: AccountId,
                                          ids: Option[Seq[UnparsedTeamMailbox]]) extends WithAccountId

case class TeamMailboxRevokeAccessResponse(accountId: AccountId,
                                           revoked: Option[Seq[TeamMailbox]],
                                           notRevoked: Option[Map[UnparsedTeamMailbox, SetError]])