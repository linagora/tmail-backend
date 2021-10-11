package com.linagora.tmail.team

import com.google.common.base.CharMatcher
import com.linagora.tmail.team.TeamMailboxName.TeamMailboxNameType
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath}

import scala.jdk.OptionConverters._

object TeamMailboxNameSpace {
  val TEAM_MAILBOX_NAMESPACE: String = MailboxConstants.NAMESPACE_PREFIX_CHAR + "TeamMailbox"
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
  def asMailAddress: MailAddress = new MailAddress(mailboxName.value.value, domain)

  def mailboxPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), mailboxName.value)

  def inboxPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), s"${mailboxName.value}.${MailboxConstants.INBOX}")

  def sentPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.fromLocalPartWithDomain("team-mailbox", domain), s"${mailboxName.value}.Sent")
}

case class TeamMailboxNotFoundException(teamMailbox: TeamMailbox) extends RuntimeException {
  override def getMessage: String = s"${teamMailbox.mailboxPath.asString()} can not be found"
}

case class TeamMailboxNameConflictException(message: String) extends RuntimeException {
  override def getMessage: String = message
}
