package com.linagora.tmail.team

import com.linagora.tmail.team.TeamMailboxRepositoryContract.{TEAM_MAILBOX_USER, TEAM_MAILBOX_USERNAME}
import org.apache.james.mailbox.model.MailboxPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.OptionConverters._

class TeamMailboxTest {

  @Test
  def fromMailboxPathShouldReturnNoneWhenNamespaceInvalid(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("namespace123", TEAM_MAILBOX_USERNAME, "sales")).toJava)
      .isEmpty
  }

  @Test
  def fromMailboxPathShouldReturnNoneWhenNameInvalid(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("namespace123", TEAM_MAILBOX_USERNAME, "sales.")).toJava)
      .isEmpty
  }

  @Test
  def fromMailboxPathShouldReturnTeamMailboxWhenValid(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_USERNAME, "sales")).toJava)
      .contains(TeamMailbox.apply(TEAM_MAILBOX_USER, TeamMailboxName.apply(TeamMailboxName.validate("sales").toOption.get)))
  }

  @Test
  def mailboxPathTeamMailboxShouldRespectAsStringMethod(): Unit = {
    val teamMailbox: TeamMailbox = TeamMailbox.from(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_USERNAME, "sales")).get
    assertThat(teamMailbox.mailboxPath.asString())
      .isEqualTo("#TeamMailbox:team-mailbox@linagora.com:sales")
    assertThat(teamMailbox.inboxPath.asString())
      .isEqualTo("#TeamMailbox:team-mailbox@linagora.com:sales.INBOX")
    assertThat(teamMailbox.sentPath.asString())
      .isEqualTo("#TeamMailbox:team-mailbox@linagora.com:sales.Sent")
  }

}
