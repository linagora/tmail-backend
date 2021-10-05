package com.linagora.tmail.team

import com.google.common.base.CharMatcher
import com.linagora.tmail.team.TeamMailboxName.TeamMailboxNameType
import com.linagora.tmail.team.TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, Username}
import org.apache.james.mailbox.model.{MailboxConstants, MailboxPath}

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

}

case class TeamMailboxName(value: TeamMailboxNameType)

object TeamMailbox {
  def from(mailboxPath: MailboxPath): Option[TeamMailbox] = mailboxPath.getNamespace match {
    case TEAM_MAILBOX_NAMESPACE => TeamMailboxName.validate(mailboxPath.getName())
      .map(nameValue => TeamMailboxName(nameValue))
      .toOption
      .map(teamMailboxName => TeamMailbox(Domain.of(mailboxPath.getUser.getLocalPart), teamMailboxName))
    case _ => None
  }
}

case class TeamMailbox(user: Domain, mailboxName: TeamMailboxName) {
  def mailboxPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.of(user.asString()), mailboxName.value)

  def inboxPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.of(user.asString()), s"${mailboxName.value}.${MailboxConstants.INBOX}")

  def sentPath: MailboxPath = new MailboxPath(TEAM_MAILBOX_NAMESPACE, Username.of(user.asString()), s"${mailboxName.value}.Sent")
}

case class TeamMailboxNotFoundException() extends RuntimeException
