package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{TeamMailboxMemberSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import com.linagora.tmail.james.jmap.model.{ParsingRequestFailure, ParsingRequestResult, ParsingRequestSuccess, TeamMailboxMemberSetRequest, TeamMailboxMemberSetResponse, TeamMailboxMemberSetResult, TeamMailboxNameDTO}
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
    update(mailboxSession.getUser, request)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serializeSetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def update(username: Username, request: TeamMailboxMemberSetRequest): SMono[TeamMailboxMemberSetResponse] =
    SFlux.fromIterable(request.validatedUpdateRequest())
      .flatMap(parsingRequestResult => update(username, parsingRequestResult))
      .collectSeq()
      .map(listResult => TeamMailboxMemberSetResponse.from(request.accountId, listResult))

  private def update(username: Username,
                     parsingRequestResult: ParsingRequestResult): SMono[TeamMailboxMemberSetResult] =
    parsingRequestResult match {
      case ParsingRequestFailure(teamMailboxNameDTO, exception) => SMono.just(TeamMailboxMemberSetResult.notUpdated(teamMailboxNameDTO, SetErrorDescription(exception.message)))
      case ParsingRequestSuccess(teamMailbox, membersUpdateToAdd, membersUpdateToRemove) =>
        checkIfAnyUsersDoesNotExist(membersUpdateToAdd, membersUpdateToRemove)
          .filter(anyUsersNotExist => anyUsersNotExist)
          .map(_ => TeamMailboxMemberSetResult.notUpdated(teamMailbox, SetErrorDescription("Some users do not exist in the system")))
          .switchIfEmpty(updateTeamMailboxMembers(username, teamMailbox, membersUpdateToAdd, membersUpdateToRemove))
          .onErrorResume {
            case _: TeamMailboxNotFoundException => SMono.just(TeamMailboxMemberSetResult.notUpdated(teamMailbox, SetErrorDescription("Team mailbox is not found")))
            case _: Throwable => SMono.just(TeamMailboxMemberSetResult.notUpdated(teamMailbox, SetErrorDescription("Internal error")))
          }
    }

  private def checkIfAnyUsersDoesNotExist(membersUpdateToAdd: List[TeamMailboxMember],
                                membersUpdateToRemove: Set[Username]): SMono[Boolean] =
    SFlux.fromIterable(membersUpdateToRemove.concat(membersUpdateToAdd.map(teamMailboxMember => teamMailboxMember.username)))
      .flatMap(username => SMono(usersRepository.containsReactive(username)))
      .filter(userExist => !userExist)
      .next()
      .map(_ => true)
      .switchIfEmpty(SMono.just(false))

  private def updateTeamMailboxMembers(username: Username,
                                       teamMailbox: TeamMailbox,
                                       membersUpdateToAdd: List[TeamMailboxMember],
                                       membersUpdateToRemove: Set[Username]): SMono[TeamMailboxMemberSetResult] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .collectMap(member => member.username, member => member)
      .flatMap(presentMembers => presentMembers.get(username) match {
        case Some(member) =>
          member.role.value match {
            case ManagerRole => updateTeamMailboxMembers(teamMailbox, membersUpdateToAdd, membersUpdateToRemove, presentMembers)
            case _ => SMono.just(TeamMailboxMemberSetResult.notUpdated(teamMailbox, SetErrorDescription(s"Not manager of teamMailbox ${teamMailbox.asString()}")))
          }
        case None => SMono.just(TeamMailboxMemberSetResult.notUpdated(teamMailbox, SetErrorDescription("Team mailbox is not found")))
      })

  private def updateTeamMailboxMembers(teamMailbox: TeamMailbox,
                                       membersUpdateToAdd: List[TeamMailboxMember],
                                       membersUpdateToRemove: Set[Username],
                                       presentMembers: Map[Username, TeamMailboxMember]): SMono[TeamMailboxMemberSetResult] =
    SFlux.fromIterable(membersUpdateToRemove.concat(membersUpdateToAdd.map(teamMailboxMember => teamMailboxMember.username)))
      .filter(username => presentMembers.contains(username) && ManagerRole.equals(presentMembers(username).role.value))
      .next()
      .map(username => TeamMailboxMemberSetResult.notUpdated(teamMailbox, SetErrorDescription(s"Could not update or remove manager ${username.asString()}")))
      .switchIfEmpty(updateMembers(teamMailbox, membersUpdateToAdd, membersUpdateToRemove))

  private def updateMembers(teamMailbox: TeamMailbox,
                            membersUpdateToAdd: List[TeamMailboxMember],
                            membersUpdateToRemove: Set[Username]): SMono[TeamMailboxMemberSetResult] =
    removeMember(teamMailbox, membersUpdateToRemove)
      .`then`(addMember(teamMailbox, membersUpdateToAdd))
      .`then`(SMono.just(TeamMailboxMemberSetResult(Option[TeamMailboxNameDTO](TeamMailboxNameDTO(teamMailbox.asString())), Option.empty)))

  private def addMember(teamMailbox: TeamMailbox, members: List[TeamMailboxMember]): SMono[Unit] =
    SFlux.fromIterable(members)
      .flatMap(member => teamMailboxRepository.addMember(teamMailbox, member))
      .`then`()

  private def removeMember(teamMailbox: TeamMailbox, usernames: Set[Username]): SMono[Unit] =
    SFlux.fromIterable(usernames)
      .flatMap(username => teamMailboxRepository.removeMember(teamMailbox, username))
      .`then`()
}