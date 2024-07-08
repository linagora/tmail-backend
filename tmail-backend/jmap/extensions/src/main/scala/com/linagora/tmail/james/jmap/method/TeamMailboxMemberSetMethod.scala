package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{TeamMailboxMemberSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import com.linagora.tmail.james.jmap.model.{ParsingRequestFailure, TeamMailboxMemberParsingRequestResult, ParsingRequestSuccess, TeamMailboxMemberSetRequest, TeamMailboxMemberSetResponse, TeamMailboxMemberSetResult, TeamMailboxNameDTO}
import com.linagora.tmail.team.TeamMemberRole.ManagerRole
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxMember, TeamMailboxNotFoundException, TeamMailboxRepository}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.user.api.UsersRepository
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

class TeamMailboxMemberSetMethod @Inject()(val teamMailboxRepository: TeamMailboxRepository,
                                           val usersRepository: UsersRepository,
                                           val metricFactory: MetricFactory,
                                           val sessionTranslator: SessionTranslator,
                                           val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[TeamMailboxMemberSetRequest] {

  override val methodName: Invocation.MethodName = MethodName("TeamMailboxMember/set")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL, LINAGORA_TEAM_MAILBOXES)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, TeamMailboxMemberSetRequest] =
    Serializer.deserializeSetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: TeamMailboxMemberSetRequest): Publisher[InvocationWithContext] =
    performUpdates(mailboxSession.getUser, request)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serializeSetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def performUpdates(username: Username, request: TeamMailboxMemberSetRequest): SMono[TeamMailboxMemberSetResponse] =
    SFlux.fromIterable(request.validatedUpdateRequest())
      .flatMap(parsingRequestResult => performUpdate(username, parsingRequestResult), ReactorUtils.LOW_CONCURRENCY)
      .collectSeq()
      .map(listResult => TeamMailboxMemberSetResponse.from(request.accountId, listResult))

  private def performUpdate(username: Username, parsingRequestResult: TeamMailboxMemberParsingRequestResult): SMono[TeamMailboxMemberSetResult] =
  {
    val r: SMono[TeamMailboxMemberSetResult] = for {
      requestWithValidSyntax <- validateSyntax(parsingRequestResult)
      requestWithUserExistenceValidation <- validateUsersExist(requestWithValidSyntax)
      presentMembers <- retrievePresentMembers(requestWithUserExistenceValidation)
      requestWithAuthorizationValidation <- validateAuthorizations(username, requestWithUserExistenceValidation, presentMembers)
      requestWithAuthorizationValidation <- validateNoChangeToManagers(requestWithAuthorizationValidation, presentMembers)
      result <- updateMembers(requestWithAuthorizationValidation)
    } yield {
      result
    }
    r.onErrorResume {
      case e: NonExistingUsers => SMono.just(TeamMailboxMemberSetResult.notUpdated(e.teamMailbox,
        SetErrorDescription(s"Some users do not exist in the system: ${e.usersNotFound.map(username => username.asString()).toArray.mkString("", ", ", "")}")))
      case e: NotAuthorized => SMono.just(TeamMailboxMemberSetResult.notUpdated(e.teamMailbox, SetErrorDescription(s"Not manager of teamMailbox ${e.teamMailbox.asString()}")))
      case e: TeamMailboxNotFoundException => SMono.just(TeamMailboxMemberSetResult.notUpdated(e.teamMailbox, SetErrorDescription("Team mailbox is not found or not a member of the mailbox")))
      case e: ManagerAuthorizationChanged => SMono.just(TeamMailboxMemberSetResult.notUpdated(e.teamMailbox, SetErrorDescription(s"Could not update or remove manager ${e.username.asString()}")))
      case e: InvalidSyntax => SMono.just(TeamMailboxMemberSetResult.notUpdated(e.failure.teamMailboxName, SetErrorDescription(e.failure.exception.message)))
    }
  }

  private def retrievePresentMembers(requestWithUserExistenceValidation: ParsingRequestSuccess): SMono[Map[Username, TeamMailboxMember]] =
    SFlux(teamMailboxRepository.listMembers(requestWithUserExistenceValidation.teamMailbox)).collectMap[Username, TeamMailboxMember](member => member.username, member => member)

  private case class NonExistingUsers(teamMailbox: TeamMailbox, usersNotFound: Seq[Username]) extends RuntimeException
  private case class InvalidSyntax(failure: ParsingRequestFailure) extends RuntimeException
  private case class NotAuthorized(teamMailbox: TeamMailbox) extends RuntimeException
  private case class ManagerAuthorizationChanged(teamMailbox: TeamMailbox, username: Username) extends RuntimeException

  private def validateSyntax(request: TeamMailboxMemberParsingRequestResult): SMono[ParsingRequestSuccess] =
    request match {
      case failure: ParsingRequestFailure => SMono.error(InvalidSyntax(failure))
      case validatedRequest: ParsingRequestSuccess => SMono.just(validatedRequest)
    }

  private def validateUsersExist(request: ParsingRequestSuccess): SMono[ParsingRequestSuccess] =
    SFlux.fromIterable(request.impactedUsers)
      .concatMap(username => SMono(usersRepository.containsReactive(username))
        .filter(userExist => !userExist)
        .map(_ => username))
      .collectSeq()
      .filter(usernames => usernames.nonEmpty)
      .flatMap(usernames => SMono.error(NonExistingUsers(request.teamMailbox, usernames)))
      .defaultIfEmpty(request)

  private def validateAuthorizations(username: Username, request: ParsingRequestSuccess, presentMembers: Map[Username, TeamMailboxMember]): SMono[ParsingRequestSuccess] =
    presentMembers.get(username) match {
      case Some(member) =>
        member.role.value match {
          case ManagerRole => SMono.just(request)
          case _ => SMono.error(NotAuthorized(request.teamMailbox))
        }
      case None => SMono.error(TeamMailboxNotFoundException(request.teamMailbox))
    }

  private def validateNoChangeToManagers(request: ParsingRequestSuccess,
                                         presentMembers: Map[Username, TeamMailboxMember]): SMono[ParsingRequestSuccess] =
    SFlux.fromIterable(request.impactedUsers)
      .filter(username => presentMembers.contains(username) && ManagerRole.equals(presentMembers(username).role.value))
      .next()
      .flatMap(username => SMono.error(ManagerAuthorizationChanged(request.teamMailbox, username)))
      .switchIfEmpty(SMono.just(request))

  private def updateMembers(request: ParsingRequestSuccess): SMono[TeamMailboxMemberSetResult] =
    removeMembers(request.teamMailbox, request.membersUpdateToRemove)
      .`then`(addMembers(request.teamMailbox, request.membersUpdateToAdd))
      .`then`(SMono.just(TeamMailboxMemberSetResult(Option[TeamMailboxNameDTO](TeamMailboxNameDTO(request.teamMailbox.asString())), Option.empty)))
      .onErrorResume {
        _: Throwable => SMono.just(TeamMailboxMemberSetResult.notUpdated(request.teamMailbox, SetErrorDescription("Internal error")))
      }

  private def addMembers(teamMailbox: TeamMailbox, members: List[TeamMailboxMember]): SMono[Unit] =
    SFlux.fromIterable(members)
      .flatMap(member => teamMailboxRepository.addMember(teamMailbox, member), ReactorUtils.LOW_CONCURRENCY)
      .`then`()

  private def removeMembers(teamMailbox: TeamMailbox, usernames: Set[Username]): SMono[Unit] =
    SFlux.fromIterable(usernames)
      .flatMap(username => teamMailboxRepository.removeMember(teamMailbox, username), ReactorUtils.LOW_CONCURRENCY)
      .`then`()
}