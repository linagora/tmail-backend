package com.linagora.tmail.team

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import org.apache.james.UserEntityValidator

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.rrt.api.CanSendFrom
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.SMono

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

class TeamMailboxModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[TeamMailboxRepositoryImpl]).in(Scopes.SINGLETON)
    bind(classOf[TMailCanSendFrom]).in(Scopes.SINGLETON)

    bind(classOf[TeamMailboxRepository]).to(classOf[TeamMailboxRepositoryImpl])
    bind(classOf[CanSendFrom]).to(classOf[TMailCanSendFrom])

    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[TeamMailboxProbe])

    Multibinder.newSetBinder(binder(), classOf[UserEntityValidator])
      .addBinding()
      .to(classOf[TeamMailboxUserEntityValidator])
  }
}
