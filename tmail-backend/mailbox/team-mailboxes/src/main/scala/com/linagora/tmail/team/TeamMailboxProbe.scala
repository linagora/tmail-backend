package com.linagora.tmail.team

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.{SFlux, SMono}

class TeamMailboxProbe @Inject()(teamMailboxRepository: TeamMailboxRepository) extends GuiceProbe {
  def create(teamMailbox: TeamMailbox): TeamMailboxProbe = {
    SMono(teamMailboxRepository.createTeamMailbox(teamMailbox)).block()
    this
  }

  def addMember(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.asMember(username))).block()
    this
  }

  def addManager(teamMailbox: TeamMailbox, username: Username): TeamMailboxProbe = {
    SMono(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.asManager(username))).block()
    this
  }

  def listMembers(teamMailbox: TeamMailbox): Seq[Username] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .map(_.username)
      .collectSeq()
      .block()

  def getMembers(teamMailbox: TeamMailbox): Seq[TeamMailboxMember] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .collectSeq()
      .block()
}
