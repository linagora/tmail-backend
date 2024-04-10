package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.TeamMailboxRevokeAccessSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import com.linagora.tmail.james.jmap.method.TeamMailboxRevokeAccessMethod.{TeamMailboxRevokeAccessFailure, TeamMailboxRevokeAccessResult, TeamMailboxRevokeAccessResults, TeamMailboxRevokeAccessSuccess}
import com.linagora.tmail.james.jmap.model.{TeamMailboxRevokeAccessRequest, TeamMailboxRevokeAccessResponse, UnparsedTeamMailbox}
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxNotFoundException, TeamMailboxRepository}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Invocation, SessionTranslator, SetError}
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

object TeamMailboxRevokeAccessMethod {
  sealed trait TeamMailboxRevokeAccessResult
  case class TeamMailboxRevokeAccessSuccess(teamMailbox: TeamMailbox) extends TeamMailboxRevokeAccessResult
  case class TeamMailboxRevokeAccessFailure(unparsedTeamMailbox: UnparsedTeamMailbox, exception: Throwable) extends TeamMailboxRevokeAccessResult {
    def asSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${unparsedTeamMailbox.value} is not a Team Mailbox: ${e.getMessage}"))
      case e: TeamMailboxNotFoundException => SetError.notFound(SetErrorDescription(e.getMessage))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class TeamMailboxRevokeAccessResults(results: Seq[TeamMailboxRevokeAccessResult]) {
    def revoked: Seq[TeamMailbox] =
      results.flatMap(result => result match {
        case success: TeamMailboxRevokeAccessSuccess => Some(success)
        case _ => None
      }).map(_.teamMailbox)

    def retrieveErrors: Map[UnparsedTeamMailbox, SetError] =
      results.flatMap(result => result match {
        case failure: TeamMailboxRevokeAccessFailure => Some(failure.unparsedTeamMailbox, failure.asSetError)
        case _ => None
      })
        .toMap
  }
}

class TeamMailboxRevokeAccessMethod @Inject()(val teamMailboxRepository: TeamMailboxRepository,
                                              val metricFactory: MetricFactory,
                                              val sessionTranslator: SessionTranslator,
                                              val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[TeamMailboxRevokeAccessRequest] with Startable {

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL, LINAGORA_TEAM_MAILBOXES)
  override val methodName: MethodName = MethodName("TeamMailbox/revokeAccess")

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, TeamMailboxRevokeAccessRequest] =
    TeamMailboxRevokeAccessSerializer.deserialize(invocation.arguments.value).asEitherRequest

  override def doProcess(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession, request: TeamMailboxRevokeAccessRequest): Publisher[InvocationWithContext] = {
    DelegatedAccountPrecondition.acceptOnlyOwnerRequest(mailboxSession, request.accountId)
    for {
      revokeResults <- revokeAccess(request, mailboxSession)
    } yield InvocationWithContext(
      invocation = Invocation(
        methodName = methodName,
        arguments = Arguments(TeamMailboxRevokeAccessSerializer.serialize(TeamMailboxRevokeAccessResponse(
          accountId = request.accountId,
          revoked = Some(revokeResults.revoked).filter(_.nonEmpty),
          notRevoked = Some(revokeResults.retrieveErrors).filter(_.nonEmpty)))),
        methodCallId = invocation.invocation.methodCallId),
      processingContext = invocation.processingContext)
  }

  def revokeAccess(request: TeamMailboxRevokeAccessRequest, mailboxSession: MailboxSession): SMono[TeamMailboxRevokeAccessResults] =
    SFlux.fromIterable(request.ids.getOrElse(Seq()))
      .flatMap(unparsedTeamMailbox => revokeAccess(unparsedTeamMailbox, mailboxSession)
        .onErrorRecover(e => TeamMailboxRevokeAccessFailure(unparsedTeamMailbox, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(TeamMailboxRevokeAccessResults)

  private def revokeAccess(unparsedTeamMailbox: UnparsedTeamMailbox, mailboxSession: MailboxSession): SMono[TeamMailboxRevokeAccessResult] =
    unparsedTeamMailbox.parse()
      .fold(e => SMono.error(e),
        teamMailbox => SMono.fromPublisher(teamMailboxRepository.removeMember(teamMailbox, mailboxSession.getUser))
          .`then`(SMono.just[TeamMailboxRevokeAccessResult](TeamMailboxRevokeAccessSuccess(teamMailbox))))
}
