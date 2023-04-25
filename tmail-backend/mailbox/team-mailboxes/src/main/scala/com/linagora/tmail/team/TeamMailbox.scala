package com.linagora.tmail.team

import com.google.common.base.CharMatcher
import com.linagora.tmail.team.TeamMailboxName.TeamMailboxNameType
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.mailbox.model.MailboxConstants.NAMESPACE_PREFIX_CHAR
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath, QuotaRoot}

import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

object TeamMailboxNameSpace {
  val TEAM_MAILBOX_NAMESPACE: String =  s"${NAMESPACE_PREFIX_CHAR}TeamMailbox"
}

object TeamMailboxName {

  type TeamMailboxNameType = String Refined TeamMailboxNameConstraint
  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('#'))

  case class TeamMailboxNameConstraint()

  implicit val validateTeamMailboxName: Validate.Plain[String, TeamMailboxNameConstraint] =
    Validate.fromPredicate(s => s.nonEmpty && s.length < 256 && charMatcher.matchesAllOf(s),
      s => s"'$s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars",
      TeamMailboxNameConstraint())

  def validate(string: String): Either[IllegalArgumentException, TeamMailboxNameType] =
    refined.refineV[TeamMailboxNameConstraint](string)
      .left
      .map(new IllegalArgumentException(_))

  def fromString(value: String): Either[IllegalArgumentException, TeamMailboxName] =
    validate(value)
      .map(TeamMailboxName(_))
}

case class TeamMailboxName(value: TeamMailboxNameType) {
  def asString(): String = value.value
}

object TeamMailbox {
  private val MAXIMUM_CHARACTERS_OF_MAIL_ADDRESS = 320
  type TeamMailboxType = String Refined TeamMailboxConstraint
  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('#'))
    .or(CharMatcher.is('.'))
    .or(CharMatcher.is('@'))

  case class TeamMailboxConstraint()

  implicit val validateTeamMailboxName: Validate.Plain[String, TeamMailboxConstraint] =
    Validate.fromPredicate(s => s.nonEmpty && s.length <= MAXIMUM_CHARACTERS_OF_MAIL_ADDRESS && charMatcher.matchesAllOf(s),
      s => s"'$s' contains some invalid characters. Should be [#a-zA-Z0-9-_.@] and no longer than 320 chars",
      TeamMailboxConstraint())

  def fromString(value: String): Either[IllegalArgumentException, TeamMailbox] =
    validate(value)
      .flatMap(toTeamMailbox)

  private def validate(string: String): Either[IllegalArgumentException, TeamMailboxType] =
    refined.refineV[TeamMailboxConstraint](string)
      .left
      .map(new IllegalArgumentException(_))

  private def toTeamMailbox(validatedTeamMailbox: TeamMailboxType): Either[IllegalArgumentException, TeamMailbox] = {
    val atPosition = validatedTeamMailbox.indexOf('@')
    if (atPosition < 0) {
      return Left(new IllegalArgumentException("Missing '@' in mailbox FQDN"))
    }
    val localPart = validatedTeamMailbox.substring(0, atPosition)
    val domainPart = validatedTeamMailbox.substring(atPosition + 1)

    Try(TeamMailbox(domain = Domain.of(domainPart),
      mailboxName = TeamMailboxName.fromString(localPart)
        .fold((e: IllegalArgumentException) => throw e,
          (teamMailboxName: TeamMailboxName) => teamMailboxName))) match {
      case Success(teamMailbox) => Right(teamMailbox)
      case Failure(throwable: IllegalArgumentException) => Left(throwable)
      case Failure(unexpectedException) => throw unexpectedException
    }
  }

  def from(mailboxPath: MailboxPath): Option[TeamMailbox] = mailboxPath.getNamespace match {
    case TEAM_MAILBOX_NAMESPACE =>
      for {
        name <- TeamMailboxName.validate(mailboxPath.getHierarchyLevels('.').get(0).getName())
          .map(nameValue => TeamMailboxName(nameValue))
          .toOption
        domain <- mailboxPath.getUser.getDomainPart.toScala
      } yield {
        TeamMailbox(domain, name)
      }
    case _ => None
  }

  def fromJava(domain: Domain, teamMailboxName: String): Option[TeamMailbox] =
    TeamMailboxName.fromString(teamMailboxName)
      .map(teamMailboxName => TeamMailbox(domain, teamMailboxName))
      .toOption

  def asTeamMailbox(mailAddress: MailAddress): Option[TeamMailbox] = for {
    name <- TeamMailboxName.validate(mailAddress.getLocalPart)
      .map(nameValue => TeamMailboxName(nameValue))
      .toOption
    domain = mailAddress.getDomain
  } yield {
    TeamMailbox(domain, name)
  }
}

case class TeamMailbox(domain: Domain, mailboxName: TeamMailboxName) {
  def owner: Username = Username.fromLocalPartWithDomain("team-mailbox", domain)

  def asMailAddress: MailAddress = new MailAddress(mailboxName.value.value, domain)

  def mailboxPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), mailboxName.value)

  def mailboxPath(subPath : String): MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), s"${mailboxName.value}.$subPath")

  def inboxPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), s"${mailboxName.value}.${MailboxConstants.INBOX}")

  def sentPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), s"${mailboxName.value}.Sent")

  def quotaRoot: QuotaRoot = QuotaRoot.quotaRoot(s"$TEAM_MAILBOX_NAMESPACE&${mailboxName.value.value}@${domain.asString()}", Some(domain).toJava)

  def asString(): String = mailboxName.asString() + "@" + domain.asString()
}

case class TeamMailboxNotFoundException(teamMailbox: TeamMailbox) extends RuntimeException {
  override def getMessage: String = s"${teamMailbox.mailboxPath.asString()} can not be found"
}

case class TeamMailboxNameConflictException(message: String) extends RuntimeException(message)
