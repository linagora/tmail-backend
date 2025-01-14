/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.james.jmap.json.{TeamMailboxMemberSerializer => Serializer}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_TEAM_MAILBOXES
import com.linagora.tmail.james.jmap.model.{TeamMailboxMemberDTO, TeamMailboxMemberGetRequest, TeamMailboxMemberGetResponse, TeamMailboxMemberRoleDTO}
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxRepository}
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE, JMAP_MAIL}
import org.apache.james.jmap.core.Invocation.{Arguments, MethodName}
import org.apache.james.jmap.core.{AccountId, Invocation, SessionTranslator}
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

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, JMAP_MAIL, LINAGORA_TEAM_MAILBOXES)

  override def getRequest(mailboxSession: MailboxSession, invocation: Invocation): Either[Exception, TeamMailboxMemberGetRequest] =
    Serializer.deserializeGetRequest(invocation.arguments.value).asEitherRequest

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

  private def getTeamMailboxMemberResponse(username: Username, request: TeamMailboxMemberGetRequest): SMono[TeamMailboxMemberGetResponse] =
    request.ids match {
      case None => getMembersOfAllTeamMailboxes(username, request.accountId)
      case Some(ids) => getMembersOfSpecificTeamMailboxes(username, request.accountId, ids)
    }

  private def getMembersOfAllTeamMailboxes(username: Username, accountId: AccountId): SMono[TeamMailboxMemberGetResponse] =
    getMembersTeamMailboxes(username, _ => true)
      .map(seq => TeamMailboxMemberGetResponse(accountId, seq, Seq.empty))

  private def getMembersOfSpecificTeamMailboxes(username: Username, accountId: AccountId, mailboxNames: Set[String]): SMono[TeamMailboxMemberGetResponse] =
    getMembersTeamMailboxes(username, teamMailbox => mailboxNames.contains(teamMailbox.mailboxName.asString()))
      .map(result => TeamMailboxMemberGetResponse(accountId, result, mailboxNames.diff(result.map(_.id).toSet).toSeq))

  private def getMembersTeamMailboxes(username: Username, filter: TeamMailbox => Boolean): SMono[Seq[TeamMailboxMemberDTO]] =
    SFlux.fromPublisher(teamMailboxRepository.listTeamMailboxes(username))
      .filter(filter)
      .flatMap(getMembersOf)
      .collectSeq()

  private def getMembersOf(teamMailbox: TeamMailbox): SMono[TeamMailboxMemberDTO] =
    SFlux(teamMailboxRepository.listMembers(teamMailbox))
      .collectMap(member => member.username.asString(), member => TeamMailboxMemberRoleDTO(member.role.value.toString))
      .map(mapMembers => TeamMailboxMemberDTO(teamMailbox.mailboxName.asString(), mapMembers))
}