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

import java.util.stream.Stream

import com.linagora.tmail.team.TeamMailboxRepositoryContract.DOMAIN_1
import com.linagora.tmail.team.TeamMemberRole.ManagerRole
import eu.timepit.refined.auto._
import org.apache.commons.lang3.tuple.Pair
import org.apache.james.core.Username
import org.apache.james.events.Event
import org.apache.james.mailbox.acl.ACLDiff
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated
import org.apache.james.mailbox.model.MailboxACL.{EntryKey, Rfc4314Rights, Right}
import org.apache.james.mailbox.model.{MailboxACL, MailboxPath, TestId}
import org.apache.james.mailbox.{MailboxManager, MailboxSession, MailboxSessionUtil}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, never, times, verify, when}
import reactor.core.scala.publisher.{SFlux, SMono}

object PropagateDeleteRightTeamMailboxListenerTest {
  val BOB: Username = Username.of("bob@localhost")
  val ALICE: Username = Username.of("alice@localhost")
  val ANDRE: Username = Username.of("andre@localhost")
  val BOB_ENTRY_KEY: EntryKey = EntryKey.createUserEntryKey(BOB)
  val ALICE_ENTRY_KEY: EntryKey = EntryKey.createUserEntryKey(ALICE)
  val TEAM_MAILBOX_MARKETING: TeamMailbox = TeamMailbox(DOMAIN_1, TeamMailboxName("marketing"))
  val TEAM_MAILBOX_MARKETING_SESSION: MailboxSession = MailboxSessionUtil.create(BOB)
  val FIT_NEW_ACL_RIGHTS: Rfc4314Rights = new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read)
  val ADD_DELETE_MAILBOX_RIGHT_COMMAND: Username => MailboxACL.ACLCommand = user => MailboxACL.command()
    .forUser(user)
    .rights(MailboxACL.Right.DeleteMailbox)
    .asAddition()

  def aclDiffNotMatchingCondition: Stream[Arguments] = {

    val oldACLDoesNotEmpty: ACLDiff = ACLDiff.computeDiff(new MailboxACL(Pair.of(BOB_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup))),
      new MailboxACL(Pair.of(BOB_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))))

    val newACLAlreadyContainDeleteMailboxRight: ACLDiff = ACLDiff.computeDiff(new MailboxACL(Pair.of(BOB_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup))),
      new MailboxACL(Pair.of(BOB_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))))

    val newACLDoesNotContainEntryKey: ACLDiff = ACLDiff.computeDiff(new MailboxACL(),
      new MailboxACL(Pair.of(ALICE_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read))))

    Stream.of(
      Arguments.of(oldACLDoesNotEmpty, BOB),
      Arguments.of(newACLAlreadyContainDeleteMailboxRight, BOB),
      Arguments.of(newACLDoesNotContainEntryKey, BOB))
  }
}

class PropagateDeleteRightTeamMailboxListenerTest {

  import PropagateDeleteRightTeamMailboxListenerTest._

  var testee: PropagateDeleteRightTeamMailboxListener = _
  var teamMailboxRepository: TeamMailboxRepository = _
  var mailboxManager: MailboxManager = _

  @BeforeEach
  def setUP(): Unit = {
    teamMailboxRepository = mock(classOf[TeamMailboxRepository])
    mailboxManager = mock(classOf[MailboxManager])
    testee = new PropagateDeleteRightTeamMailboxListener(teamMailboxRepository, mailboxManager)

    when(mailboxManager.applyRightsCommandReactive(any(), any(), any())).thenReturn(SMono.empty)
    when(mailboxManager.createSystemSession(TEAM_MAILBOX_MARKETING.owner)).thenReturn(TEAM_MAILBOX_MARKETING_SESSION)
  }

  @Test
  def shouldNotPropagateDeleteMailboxRightWhenNotATeamMailbox(): Unit = {
    // Given
    val notTeamMailboxPath: MailboxPath = MailboxPath.forUser(Username.of("bob"), "mailbox")

    val mailboxACLUpdated = new MailboxACLUpdated(MailboxSession.SessionId.of(42),
      BOB, notTeamMailboxPath,
      ACLDiff.computeDiff(new MailboxACL(Pair.of(BOB_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup))),
        new MailboxACL(Pair.of(BOB_ENTRY_KEY, FIT_NEW_ACL_RIGHTS))),
      TestId.of(18), Event.EventId.random)

    // When
    SMono(testee.reactiveEvent(mailboxACLUpdated)).block()

    // Then
    verify(teamMailboxRepository, never()).listMembers(any())
    verify(mailboxManager, never()).applyRightsCommandReactive(any(), any(), any())
  }

  @ParameterizedTest
  @MethodSource(value = Array("aclDiffNotMatchingCondition"))
  def shouldNotPropagateDeleteMailboxRightWhenACLDiffIsNotMatch(aclDiff: ACLDiff, member: Username): Unit = {
    assertThat(testee.needsToAddRight(aclDiff, member)).isFalse
  }

  @Test
  def shouldPropagateDeleteMailboxRightWhenACLDiffMatch(): Unit = {
    // Given
    when(teamMailboxRepository.listMembers(TEAM_MAILBOX_MARKETING)).thenReturn(SFlux.fromIterable(List(new TeamMailboxMember(BOB, TeamMemberRole(ManagerRole)))))
    val teamMailboxPath: MailboxPath = TEAM_MAILBOX_MARKETING.mailboxPath("sub1")

    val mailboxACLUpdated = new MailboxACLUpdated(
      MailboxSession.SessionId.of(42),
      TEAM_MAILBOX_MARKETING.owner, teamMailboxPath,
      ACLDiff.computeDiff(new MailboxACL,
        new MailboxACL(Pair.of(BOB_ENTRY_KEY, FIT_NEW_ACL_RIGHTS))),
      TestId.of(18),
      Event.EventId.random)

    // When
    SMono(testee.reactiveEvent(mailboxACLUpdated)).block()

    // Then
    verify(mailboxManager, times(1)).applyRightsCommandReactive(
      teamMailboxPath,
      ADD_DELETE_MAILBOX_RIGHT_COMMAND.apply(BOB),
      TEAM_MAILBOX_MARKETING_SESSION)
  }

  @Test
  def shouldPropagateDeleteMailboxRightForCorrectTeamMembers(): Unit = {
    // Given
    when(teamMailboxRepository.listMembers(TEAM_MAILBOX_MARKETING)).thenReturn(SFlux.fromIterable(List(
      new TeamMailboxMember(BOB, TeamMemberRole(ManagerRole)),
      new TeamMailboxMember(ALICE, TeamMemberRole(ManagerRole)))))

    val teamMailboxPath: MailboxPath = TEAM_MAILBOX_MARKETING.mailboxPath("sub1")

    val mailboxACLUpdated = new MailboxACLUpdated(
      MailboxSession.SessionId.of(42),
      TEAM_MAILBOX_MARKETING.owner, teamMailboxPath,
      ACLDiff.computeDiff(new MailboxACL,
        new MailboxACL(Pair.of(BOB_ENTRY_KEY, new MailboxACL.Rfc4314Rights(Right.Lookup, Right.Read)),
          Pair.of(ALICE_ENTRY_KEY, FIT_NEW_ACL_RIGHTS))),
      TestId.of(18),
      Event.EventId.random)

    // When
    SMono(testee.reactiveEvent(mailboxACLUpdated)).block()

    // Then
    verify(mailboxManager, times(1)).applyRightsCommandReactive(
      teamMailboxPath, ADD_DELETE_MAILBOX_RIGHT_COMMAND.apply(BOB), TEAM_MAILBOX_MARKETING_SESSION)

    verify(mailboxManager, times(1)).applyRightsCommandReactive(
      teamMailboxPath,
      ADD_DELETE_MAILBOX_RIGHT_COMMAND.apply(ALICE),
      TEAM_MAILBOX_MARKETING_SESSION)

    verify(mailboxManager, times(0)).applyRightsCommandReactive(
      teamMailboxPath,
      ADD_DELETE_MAILBOX_RIGHT_COMMAND.apply(ANDRE),
      TEAM_MAILBOX_MARKETING_SESSION)
  }
}