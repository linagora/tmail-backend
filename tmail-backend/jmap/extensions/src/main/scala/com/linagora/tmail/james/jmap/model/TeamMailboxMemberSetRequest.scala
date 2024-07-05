package com.linagora.tmail.james.jmap.model

import cats.implicits._
import com.linagora.tmail.team.{TeamMailbox, TeamMailboxMember}
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.method.WithAccountId

import scala.util.Try

trait ParsingRequestException extends RuntimeException {
  def message: String

  override def getMessage: String = message
}
case class InvalidTeamMailboxException(teamMailboxNameDTO: TeamMailboxNameDTO) extends ParsingRequestException {
  override def message: String = s"Invalid teamMailboxName"
}

case class InvalidRoleException(role: TeamMailboxMemberRoleDTO) extends ParsingRequestException {
  override def message: String = s"Invalid role: ${role.role}"
}

case class InvalidTeamMemberNameException(memberName: TeamMailboxMemberName) extends ParsingRequestException {
  override def message: String = s"Invalid team member name: ${memberName.value}"
}

trait ParsingRequestResult

case class ParsingRequestSuccess(teamMailbox: TeamMailbox,
                                 membersUpdateToAdd: List[TeamMailboxMember],
                                 membersUpdateToRemove: Set[Username]) extends ParsingRequestResult
case class ParsingRequestFailure(tmbNameDto: TeamMailboxNameDTO, exception: ParsingRequestException) extends ParsingRequestResult

case class TeamMailboxMemberSetRequest(accountId: AccountId,
                                       update: Map[TeamMailboxNameDTO, Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]]) extends WithAccountId {
  def validatedUpdateRequest(): List[ParsingRequestResult] =
    update.map {
      case (teamMailboxNameDTO, membersUpdate) =>
        (for {
          teamMailbox <- teamMailboxNameDTO.validate
          validatedMembers <- validateMemberName(membersUpdate)
          addMembers <- getAddMembers(validatedMembers)
          removeMembers = getRemoveMembers(validatedMembers)
        } yield ParsingRequestSuccess(teamMailbox, addMembers, removeMembers))
          .fold(e => ParsingRequestFailure(teamMailboxNameDTO, e), identity)
    }.toList

  private def validateMemberName(map: Map[TeamMailboxMemberName, Option[TeamMailboxMemberRoleDTO]]): Either[ParsingRequestException, Map[Username, Option[TeamMailboxMemberRoleDTO]]] =
    map.toList.traverse {
      case (memberName, role) => memberName.validate.map(_ -> role)
    }.map(_.toMap)

  private def getAddMembers(map: Map[Username, Option[TeamMailboxMemberRoleDTO]]): Either[ParsingRequestException, List[TeamMailboxMember]] =
    map.collect {
      case (memberName, Some(role)) => role.validate.map(TeamMailboxMember(memberName, _))
    }.toList.sequence

  private def getRemoveMembers(map: Map[Username, Option[TeamMailboxMemberRoleDTO]]): Set[Username] =
    map.collect {
      case (memberName, None) => memberName
    }.toSet
}

object TeamMailboxMemberSetResponse {
  def from(accountId: AccountId, list: Seq[TeamMailboxMemberSetResult]): TeamMailboxMemberSetResponse =
    TeamMailboxMemberSetResponse(accountId,
      list.filter(setResult => setResult.updated.nonEmpty)
        .map(setResult => setResult.updated.get -> "").toMap,
      list.filter(setResult => setResult.notUpdated.nonEmpty)
        .map(setResult => setResult.notUpdated.get.teamMailboxName -> setResult.notUpdated.get.error).toMap)
}
case class TeamMailboxMemberSetResponse(accountId: AccountId,
                                        updated: Map[TeamMailboxNameDTO, String],
                                        notUpdated: Map[TeamMailboxNameDTO, SetError])

case class TeamMailboxNameDTO(value: String) {
  def validate: Either[InvalidTeamMailboxException, TeamMailbox] = TeamMailbox.fromString(value)
    .left.map(_ => InvalidTeamMailboxException(this))
}

case class TeamMailboxMemberName(value: String)  {
  def validate: Either[InvalidTeamMemberNameException, Username] = Try(Username.of(value))
    .toEither.left.map(_ =>  InvalidTeamMemberNameException(this))
}

case class TeamMailboxMemberSetFailure(teamMailboxName: TeamMailboxNameDTO,
                                       error: SetError)
object TeamMailboxMemberSetResult {
  def notUpdated(teamMailboxNameDTO: TeamMailboxNameDTO, setErrorDescription: SetErrorDescription): TeamMailboxMemberSetResult =
    TeamMailboxMemberSetResult(notUpdated = Some(TeamMailboxMemberSetFailure(teamMailboxNameDTO, SetError.invalidPatch(setErrorDescription))))
  def notUpdated(teamMailbox: TeamMailbox, setErrorDescription: SetErrorDescription): TeamMailboxMemberSetResult =
    TeamMailboxMemberSetResult(notUpdated = Some(TeamMailboxMemberSetFailure(TeamMailboxNameDTO(teamMailbox.asString()), SetError.invalidPatch(setErrorDescription))))
  def updated(teamMailboxName: TeamMailboxNameDTO): TeamMailboxMemberSetResult =
    TeamMailboxMemberSetResult(updated = Some(teamMailboxName))
}

case class TeamMailboxMemberSetResult(updated: Option[TeamMailboxNameDTO] = None,
                                      notUpdated: Option[TeamMailboxMemberSetFailure] = None)