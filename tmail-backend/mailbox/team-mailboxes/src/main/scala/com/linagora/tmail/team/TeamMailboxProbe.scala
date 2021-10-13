package com.linagora.tmail.team

import org.apache.james.core.Username
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.SMono

import javax.inject.Inject

class TeamMailboxProbe @Inject()(teamMailboxRepository: TeamMailboxRepository) extends GuiceProbe {
  def create(teamMailbox: TeamMailbox): TeamMailboxProbe = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    this
  }

  def addMember(teamMailbox: TeamMailbox, member: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.addMember(teamMailbox, member)).block()
    this
  }
}
