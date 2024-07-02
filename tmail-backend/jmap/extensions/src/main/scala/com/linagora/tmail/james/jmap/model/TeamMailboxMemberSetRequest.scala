package com.linagora.tmail.james.jmap.model

import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.method.WithAccountId

case class TeamMailboxMemberSetRequest(accountId: AccountId,
                                       update: Map[TeamMailboxNameDTO, Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]]) extends WithAccountId

case class TeamMailboxMemberSetResponse(accountId: AccountId,
                                        updated: Map[TeamMailboxNameDTO, String],
                                        notUpdated: Map[TeamMailboxNameDTO, SetError])

case class TeamMailboxNameDTO(value: String)

case class TeamMailboxMemberName(value: String)

case class TeamMailboxMemberSetFailure(teamMailboxName: TeamMailboxNameDTO,
                                       error: SetError)

case class TeamMailboxMemberSetResult(updated: Option[TeamMailboxNameDTO],
                                      notUpdated: Option[TeamMailboxMemberSetFailure])