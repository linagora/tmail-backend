package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{TeamMailboxMemberSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import com.linagora.tmail.james.jmap.model.{TeamMailboxMemberName, TeamMailboxMemberRoleDTO, TeamMailboxMemberSetFailure, TeamMailboxMemberSetRequest, TeamMailboxMemberSetResponse, TeamMailboxMemberSetResult, TeamMailboxNameDTO}
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxMember, TeamMailboxRepository, TeamMemberRole}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Invocation, SessionTranslator, SetError}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

class TeamMailboxMemberSetMethod @Inject()(val teamMailboxRepository: TeamMailboxRepository,
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
    setTeamMailboxMemberResponse(mailboxSession.getUser, request)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serializeSetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def setTeamMailboxMemberResponse(username: Username, request: TeamMailboxMemberSetRequest): SMono[TeamMailboxMemberSetResponse] = {
    val teamMailboxes = request.update.keys.map(teamMailboxNameDTO => TeamMailbox.fromString(teamMailboxNameDTO.value).toOption)
      .filter(maybeTeamMailbox => maybeTeamMailbox.nonEmpty)
      .map(maybeTeamMailbox => maybeTeamMailbox.get)
    val invalidTeamMailboxNameFailureResult = request.update.keys.map(teamMailboxName => TeamMailbox.fromString(teamMailboxName.value) match {
      case Left(_) => Some(TeamMailboxMemberSetFailure(teamMailboxName, SetError.invalidPatch(SetErrorDescription(s"Invalid teamMailboxName ${teamMailboxName.value}"))))
      case Right(_) => None
    }).filter(option => option.nonEmpty)
      .map(option => TeamMailboxMemberSetResult(Option.empty, option))

    SFlux.fromIterable(teamMailboxes)
      .flatMap(teamMailbox => updateTeamMailboxMembers(username, teamMailbox, request.update(TeamMailboxNameDTO(teamMailbox.asString()))))
      .collectSeq()
      .map(teamMailboxMemberSetResults => TeamMailboxMemberSetResponse(request.accountId,
        teamMailboxMemberSetResults.filter(setResult => setResult.updated.nonEmpty)
          .map(setResult => setResult.updated.get -> "").toMap,
        invalidTeamMailboxNameFailureResult.concat(teamMailboxMemberSetResults.filter(setResult => setResult.notUpdated.nonEmpty))
          .map(setResult => setResult.notUpdated.get.teamMailboxName -> setResult.notUpdated.get.error).toMap))
  }

  private def updateTeamMailboxMembers(username: Username,
                                   teamMailbox: TeamMailbox,
                                   mapMemberNameToRole: Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]): SMono[TeamMailboxMemberSetResult] = {
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .collectMap(member => member.username, member => member)
      .flatMap(mapExistedMembers => mapExistedMembers.get(username) match {
          case Some(teamMailboxMember) =>
            if (TeamMemberRole.ManagerRole.equals(teamMailboxMember.role.value)) {
              updateTeamMailboxMembers(teamMailbox, mapMemberNameToRole, mapExistedMembers)
            } else {
              SMono.just(createTeamMailboxMemberSetResult(teamMailbox, SetErrorDescription(s"Not manager of teamMailbox ${teamMailbox.asString()}")))
            }
          case None => SMono.just(createTeamMailboxMemberSetResult(teamMailbox, SetErrorDescription(s"Wrong teamMailboxName ${teamMailbox.asString()}")))
      })
  }

  private def updateTeamMailboxMembers(teamMailbox: TeamMailbox,
                  mapMemberNameToRole: Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]],
                  mapExistedMembers: Map[Username, TeamMailboxMember]): SMono[TeamMailboxMemberSetResult] = {
    if (checkIfAnyRolesInvalid(mapMemberNameToRole)) {
      SMono.just(createTeamMailboxMemberSetResult(teamMailbox, SetErrorDescription(s"Role could only be manager or member")))
    } else {
      if (checkIfAnyManagers(mapMemberNameToRole, mapExistedMembers)) {
        SMono.just(createTeamMailboxMemberSetResult(teamMailbox, SetErrorDescription(s"Could not update or remove a manager")))
      } else {
        SFlux.fromIterable(mapMemberNameToRole).flatMap(pairMemberNameAndRole => {
          pairMemberNameAndRole._2 match {
            case Some(role) => SMono(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.of(Username.of(pairMemberNameAndRole._1.value), TeamMemberRole.from(role.role).get)))
            case None => SMono(teamMailboxRepository.removeMember(teamMailbox, Username.of(pairMemberNameAndRole._1.value)))
          }
        }).collectSeq().`then`(SMono.just(TeamMailboxMemberSetResult(Option[TeamMailboxNameDTO](TeamMailboxNameDTO(teamMailbox.asString())), Option.empty)))
      }
    }
  }

  private def checkIfAnyRolesInvalid(mapMemberNameToRole: Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]): Boolean = {
    mapMemberNameToRole.values.filter(option => option.nonEmpty).map(roleDTO => TeamMemberRole.from(roleDTO.get.role)).exists(maybeRole => maybeRole.isEmpty)
  }

  private def checkIfAnyManagers(mapMemberNameToRole: Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]],
                                 mapExistedMembers: Map[Username, TeamMailboxMember]): Boolean = {
    mapExistedMembers.filter(pairMemberNameAndRole => TeamMemberRole.ManagerRole.equals(pairMemberNameAndRole._2.role.value))
      .exists(pairMemberNameAndRole =>
        mapMemberNameToRole.keys.map(memberName => Username.of(memberName.value)).toSet.contains(pairMemberNameAndRole._1))
  }

  private def createTeamMailboxMemberSetResult(teamMailbox: TeamMailbox, setErrorDescription: SetErrorDescription): TeamMailboxMemberSetResult = {
    TeamMailboxMemberSetResult(Option.empty,
      Option[TeamMailboxMemberSetFailure](TeamMailboxMemberSetFailure(TeamMailboxNameDTO(teamMailbox.asString()),
        SetError.invalidPatch(setErrorDescription))))
  }
}