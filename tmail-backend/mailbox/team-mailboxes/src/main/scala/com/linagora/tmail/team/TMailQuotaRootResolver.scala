package com.linagora.tmail.team

import com.google.inject.Inject
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{Mailbox, MailboxPath, QuotaRoot}
import org.apache.james.mailbox.store.MailboxSessionMapperFactory
import org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver
import org.apache.james.mailbox.{MailboxSession, SessionProvider}
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

class TMailQuotaRootResolver @Inject()(sessionProvider: SessionProvider,
                                       factory: MailboxSessionMapperFactory,
                                       teamMailboxRepository: TeamMailboxRepository) extends DefaultUserQuotaRootResolver(sessionProvider, factory) {
  override def getQuotaRoot(mailboxPath: MailboxPath): QuotaRoot = mailboxPath.getNamespace match {
    case TEAM_MAILBOX_NAMESPACE => TeamMailbox.from(mailboxPath)
      .fold(throw new IllegalArgumentException(s"Invalid team mailbox $mailboxPath"))(_.quotaRoot)
    case _ => super.getQuotaRoot(mailboxPath)
  }

  override def forMailAddress(username: Username): QuotaRoot = TeamMailbox.asTeamMailbox(username.asMailAddress())
    .fold(super.forUser(username))(teamMailbox => if (SMono(teamMailboxRepository.exists(teamMailbox)).block()) {
      teamMailbox.quotaRoot
    } else {
      super.forUser(username)
    })

  override def retrieveAssociatedMailboxes(quotaRoot: QuotaRoot, session: MailboxSession): Flux[Mailbox] =
    Flux.from(Try(DefaultUserQuotaRootResolver.QUOTA_ROOT_DESERIALIZER.toParts(quotaRoot.getValue))
      .fold(SFlux.error[Mailbox](_),
        parts => associatedMailboxes(namespace = parts.get(0), user = parts.get(1), session = session)))

  private def associatedMailboxes(namespace: String, user: String, session: MailboxSession): SFlux[Mailbox] = namespace match {
      case TEAM_MAILBOX_NAMESPACE => TeamMailbox.asTeamMailbox(new MailAddress(user))
        .fold(SFlux.empty[Mailbox])(teamMailbox => retrieveAssociatedMailboxes(teamMailbox, session))
      case otherNamespace => SFlux(super.retrieveAssociatedMailboxes(session, otherNamespace, user))
    }

  private def retrieveAssociatedMailboxes(teamMailbox: TeamMailbox, session: MailboxSession): SFlux[Mailbox] = {
    val mailboxMapper = factory.getMailboxMapper(session)
    SFlux.just(teamMailbox.mailboxPath, teamMailbox.inboxPath, teamMailbox.sentPath)
      .flatMap(path => SMono(mailboxMapper.findMailboxByPath(path))
        .onErrorResume {
          case _: MailboxNotFoundException => SMono.empty
          case e => SMono.error(e)
        })
  }

  override def associatedUsername(quotaRoot: QuotaRoot): Username =
    Username.of(DefaultUserQuotaRootResolver.QUOTA_ROOT_DESERIALIZER.toParts(quotaRoot.getValue).get(1))
}
