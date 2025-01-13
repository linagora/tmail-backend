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

package com.linagora.tmail.team

import java.util.Optional

import com.linagora.tmail.team.TeamMailboxRepositoryContract.{DOMAIN_1, TEAM_MAILBOX_DOMAIN_1}
import eu.timepit.refined.auto._
import org.apache.james.core.{Domain, MailAddress}
import org.apache.james.mailbox.model.{MailboxPath, QuotaRoot}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import scala.jdk.OptionConverters._

class TeamMailboxTest {

  @Test
  def fromMailboxPathShouldReturnNoneWhenNamespaceInvalid(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("namespace123", TEAM_MAILBOX_DOMAIN_1, "sales")).toJava)
      .isEmpty
  }

  @Test
  def fromMailboxPathShouldReturnNoneWhenNameInvalid(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("namespace123", TEAM_MAILBOX_DOMAIN_1, "sales.")).toJava)
      .isEmpty
  }

  @Test
  def fromMailboxPathShouldReturnTeamMailboxWhenValid(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_DOMAIN_1, "sales")).toJava)
      .contains(TeamMailbox(DOMAIN_1, TeamMailboxName("sales")))
  }

  @Test
  def fromMailboxPathShouldReturnTeamMailboxWhenValidSubMailbox(): Unit = {
    assertThat(TeamMailbox.from(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_DOMAIN_1, "sales.INBOX")).toJava)
      .contains(TeamMailbox(DOMAIN_1, TeamMailboxName("sales")))
  }

  @Test
  def quotaRootShouldReturnDesiredValue(): Unit = {
    assertThat(TeamMailbox(DOMAIN_1, TeamMailboxName("sales")).quotaRoot)
      .isEqualTo(QuotaRoot.quotaRoot("#TeamMailbox&sales@linagora.com", Optional.of(Domain.of("linagora.com"))))
  }

  @Test
  def asTeamMailboxMailAddressShouldReturnTeamMailboxWhenValid(): Unit = {
    assertThat(TeamMailbox.asTeamMailbox(new MailAddress("sales@linagora.com")).toJava)
      .contains(TeamMailbox(Domain.of("linagora.com"), TeamMailboxName("sales")))
  }

  @Test
  def asTeamMailboxMailAddressShouldReturnEmptyWhenInvalid(): Unit = {
    assertThat(TeamMailbox.asTeamMailbox(new MailAddress("sales.invaalid@linagora.com")).toJava)
      .isEmpty
  }

  @Test
  def mailboxPathTeamMailboxShouldRespectAsStringMethod(): Unit = {
    val teamMailbox: TeamMailbox = TeamMailbox.from(new MailboxPath("#TeamMailbox", TEAM_MAILBOX_DOMAIN_1, "sales")).get
    assertThat(teamMailbox.mailboxPath.asString())
      .isEqualTo("#TeamMailbox:team-mailbox@linagora.com:sales")
    assertThat(teamMailbox.inboxPath.asString())
      .isEqualTo("#TeamMailbox:team-mailbox@linagora.com:sales.INBOX")
    assertThat(teamMailbox.sentPath.asString())
      .isEqualTo("#TeamMailbox:team-mailbox@linagora.com:sales.Sent")
  }

}
