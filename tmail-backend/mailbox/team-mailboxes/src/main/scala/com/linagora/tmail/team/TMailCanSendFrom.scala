package com.linagora.tmail.team

import java.util.stream

import javax.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.rrt.api.{AliasReverseResolver, RecipientRewriteTable}
import org.apache.james.rrt.lib.CanSendFromImpl
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono

class TMailCanSendFrom @Inject()(rrt: RecipientRewriteTable, aliasReverseResolver: AliasReverseResolver, teamMailboxRepository: TeamMailboxRepository) extends CanSendFromImpl(rrt, aliasReverseResolver) {
  override def userCanSendFrom(connectedUser: Username, fromUser: Username): Boolean =
    super.userCanSendFrom(connectedUser, fromUser) || validTeamMailbox(connectedUser, fromUser)

  private def validTeamMailbox(connectedUser: Username, fromUser: Username): Boolean =
    TeamMailbox.asTeamMailbox(fromUser.asMailAddress()) match {
      case Some(teamMailbox) => SMono(teamMailboxRepository.listMembers(teamMailbox))
        .filter(connectedUser.equals(_))
        .hasElement
        .onErrorResume {
          case _: TeamMailboxNotFoundException => SMono.just(false)
          case e => SMono.error(e)
        }
        .block()
      case None => false
    }

  override def allValidFromAddressesForUser(user: Username): stream.Stream[MailAddress] = stream.Stream.concat(
    super.allValidFromAddressesForUser(user),
    Flux.from(teamMailboxRepository.listTeamMailboxes(user)).map(_.asMailAddress).toStream)
}
