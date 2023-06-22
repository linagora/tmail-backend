package com.linagora.tmail.team

import org.reactivestreams.Publisher

trait TeamMailboxCallback {
  def teamMailboxAdded(teamMailbox: TeamMailbox): Publisher[Void]

  def teamMailboxRemoved(teamMailbox: TeamMailbox): Publisher[Void]
}
