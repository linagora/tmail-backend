package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{TeamMailboxMemberSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import com.linagora.tmail.james.jmap.model.{TeamMailboxMemberDTO, TeamMailboxMemberGetRequest, TeamMailboxMemberGetResponse, TeamMailboxMemberRoleDTO}
import com.linagora.tmail.team.TeamMailboxRepository
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, Invocation, SessionTranslator}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.{InvocationWithContext, MethodRequiringAccountId}
import org.apache.james.jmap.routes.SessionSupplier
import org.apache.james.mailbox.MailboxSession
import org.apache.james.metrics.api.MetricFactory
import org.reactivestreams.Publisher
import play.api.libs.json.JsObject
import reactor.core.scala.publisher.{SFlux, SMono}

class TeamMailboxMemberGetMethod @Inject()(val teamMailboxRepository: TeamMailboxRepository,
                                           val metricFactory: MetricFactory,
                                           val sessionTranslator: SessionTranslator,
                                           val sessionSupplier: SessionSupplier) extends MethodRequiringAccountId[TeamMailboxMemberGetRequest] {

  override val methodName: Invocation.MethodName = MethodName("TeamMailboxMember/get")

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_TEAM_MAILBOXES)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, TeamMailboxMemberGetRequest] =
    Serializer.deserializeGetRequest(invocation.arguments.value)
      .asEither.left.map(ResponseSerializer.asException)

  override def doProcess(capabilities: Set[CapabilityIdentifier],
                         invocation: InvocationWithContext,
                         mailboxSession: MailboxSession,
                         request: TeamMailboxMemberGetRequest): Publisher[InvocationWithContext] =
    getTeamMailboxMemberResponse(mailboxSession.getUser, request)
      .map(response => Invocation(
        methodName = methodName,
        arguments = Arguments(Serializer.serializeGetResponse(response).as[JsObject]),
        methodCallId = invocation.invocation.methodCallId))
      .map(InvocationWithContext(_, invocation.processingContext))

  private def getTeamMailboxMemberResponse(username: Username, request: TeamMailboxMemberGetRequest): SMono[TeamMailboxMemberGetResponse] = {
    request.ids match {
      case None => getMembersOfAllTeamMailboxes(username, request.accountId)
      case Some(ids) => getMembersOfSpecificTeamMailboxes(username, request.accountId, ids)
    }
  }

  private def getMembersOfAllTeamMailboxes(username: Username, accountId: AccountId): SMono[TeamMailboxMemberGetResponse] =
    SFlux.fromPublisher(teamMailboxRepository.listTeamMailboxes(username))
      .flatMap(teamMailbox =>
        SFlux(teamMailboxRepository.listMembers(teamMailbox))
          .collectMap(member => member.username.asString(), member => TeamMailboxMemberRoleDTO(member.role.value.toString))
          .map(mapMembers => TeamMailboxMemberDTO(teamMailbox.mailboxName.asString(), mapMembers)))
      .collectSeq()
      .map(seq => TeamMailboxMemberGetResponse(accountId, seq, Seq.empty))

  private def getMembersOfSpecificTeamMailboxes(username: Username, accountId: AccountId, mailboxNames: Set[String]): SMono[TeamMailboxMemberGetResponse] = {
    SFlux.fromPublisher(teamMailboxRepository.listTeamMailboxes(username))
      .collectSeq()
      .flatMap(teamMailboxes => {
        val foundTeamMailboxes = teamMailboxes.filter(teamMailbox => mailboxNames.contains(teamMailbox.mailboxName.asString()))
        val notFoundTeamMailboxNames = mailboxNames.diff(foundTeamMailboxes.map(teamMailbox => teamMailbox.mailboxName.asString()).toSet).toSeq
        SFlux.fromIterable(foundTeamMailboxes)
          .flatMap(teamMailbox =>
            SFlux(teamMailboxRepository.listMembers(teamMailbox))
              .collectMap(member => member.username.asString(), member => TeamMailboxMemberRoleDTO(member.role.value.toString))
              .map(mapMembers => TeamMailboxMemberDTO(teamMailbox.mailboxName.asString(), mapMembers)))
          .collectSeq()
          .map(seq => TeamMailboxMemberGetResponse(accountId, seq, notFoundTeamMailboxNames));
      })
  }
}