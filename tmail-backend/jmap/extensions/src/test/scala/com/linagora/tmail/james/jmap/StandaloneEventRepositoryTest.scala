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

package com.linagora.tmail.james.jmap

import java.util
import java.util.Optional

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.method.CalendarEventReplyPerformer
import com.linagora.tmail.james.jmap.model.{CalendarEventAttendanceResults, EventAttendanceStatusEntry, LanguageLocation}
import jakarta.mail.Flags
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.james.jmap.mail.{BlobId, PartId}
import org.apache.james.mailbox.exception.MailboxException
import org.apache.james.mailbox.fixture.MailboxFixture
import org.apache.james.mailbox.fixture.MailboxFixture.ALICE
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{FetchGroup, Mailbox, MessageId, TestMessageId}
import org.apache.james.mailbox.store.MessageIdManagerTestSystem
import org.apache.james.mailbox.{MailboxSession, MailboxSessionUtil, MessageUid}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

import scala.util.Random

class StandaloneEventRepositoryTest {
  var calendarEventReplyPerformer: CalendarEventReplyPerformer = _
  var session: MailboxSession = _
  var testee: StandaloneEventRepository = _
  var messageIdManagerTestSystem: MessageIdManagerTestSystem = _
  var mailbox: Mailbox = _

  @BeforeEach
  @throws[MailboxException]
  def setUp(): Unit = {
    messageIdManagerTestSystem = createTestSystem
    calendarEventReplyPerformer = mock(classOf[CalendarEventReplyPerformer])
    when(calendarEventReplyPerformer.process(
      any[Seq[BlobId]],
      any[Option[LanguageLocation]],
      any(classOf[PartStat]),
      any(classOf[MailboxSession])))
      .thenReturn(SMono.empty)

    testee = new StandaloneEventRepository(messageIdManagerTestSystem.getMessageIdManager, messageIdManagerTestSystem.getMailboxManager.getSessionProvider, calendarEventReplyPerformer, new TestMessageId.Factory)
    session = MailboxSessionUtil.create(ALICE)
    mailbox = messageIdManagerTestSystem.createMailbox(MailboxFixture.INBOX_ALICE, session)
  }

  @Test
  def givenAcceptedFlagIsLinkedToMailGetAttendanceStatusShouldReturnAccepted(): Unit = {
    val flags: Flags = new Flags("$accepted")
    val messageId: MessageId = createMessage(flags)
    val blobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(blobId)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, blobIds)).block())
      .isEqualTo(done(blobId, AttendanceStatus.Accepted))
  }

  @Test
  def givenRejectedFlagIsLinkedToMailGetAttendanceStatusShouldReturnDeclined(): Unit = {
    val flags: Flags = new Flags("$rejected")
    val messageId: MessageId = createMessage(flags)
    val blobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(blobId)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, blobIds)).block())
      .isEqualTo(done(blobId, AttendanceStatus.Declined))
  }

  @Test
  def givenTentativelyAcceptedFlagIsLinkedToMailGetAttendanceStatusShouldReturnTentative(): Unit = {
    val flags: Flags = new Flags("$tentativelyaccepted")
    val messageId: MessageId = createMessage(flags)
    val blobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(blobId)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, blobIds)).block())
      .isEqualTo(done(blobId, AttendanceStatus.Tentative))
  }

  // It should also print a warning message
  @Test
  def givenMoreThanEventAttendanceFlagIsLinkedToMailGetAttendanceStatusShouldReturnAny(): Unit = {
    val flags: Flags = new Flags("$rejected")
    flags.add("$accepted")
    val messageId: MessageId = createMessage(flags)
    val blobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(blobId)

    assertThat(util.List.of((blobId, AttendanceStatus.Accepted), done(blobId, AttendanceStatus.Declined))
      .contains(Mono.from(testee.getAttendanceStatus(mailbox.getUser, blobIds)).block()))
  }

  @Test
  def getAttendanceStatusShouldFallbackToNeedsActionWhenNoFlagIsLinkedToMail(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val blobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = ImmutableList.of(blobId)

    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, blobIds)).block())
      .isEqualTo(done(blobId, AttendanceStatus.NeedsAction))
  }

  @Test
  def setAttendanceStatusShouldSetAcceptedFlag(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(calendaerEventBlobId)

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Accepted, blobIds, Optional.empty)).block

    val updatedFlags: Flags = getFlags(messageId)

    assertThat(updatedFlags.contains("$accepted")).isTrue
  }

  @Test
  def setAttendanceStatusShouldSetDeclinedFlag(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(calendaerEventBlobId)

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Declined, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$rejected")).isTrue
  }

  @Test
  def setAttendanceStatusShouldSetTentativeFlag(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(calendaerEventBlobId)

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Tentative, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$tentativelyaccepted")).isTrue
  }

  @Test
  def setAttendanceStatusShouldRemoveExistingEventAttendanceFlags(): Unit = {
    val flags: Flags = new Flags("$accepted")
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(calendaerEventBlobId)

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Declined, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$accepted")).isFalse
    assertThat(updatedFlags.contains("$rejected")).isTrue
  }

  @Test
  def setAttendanceStatusShouldRemoveExistingEventAttendanceFlagsWhenNeedsActionSet(): Unit = {
    val flags: Flags = new Flags("$rejected")
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(calendaerEventBlobId)

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.NeedsAction, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$rejected")).isFalse
    assertThat(updatedFlags.contains("$needs-action")).isTrue
  }

  @Test
  def setAttendanceStatusShouldBeIdempotent(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds = util.List.of(calendaerEventBlobId)

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Accepted, blobIds, Optional.empty)).block
    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Accepted, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$accepted")).isTrue
    assertThat(updatedFlags.getUserFlags.length).isEqualTo(1)
  }

  private def getFlags(messageId: MessageId): Flags =
    Mono.from(messageIdManagerTestSystem.getMessageIdManager.getMessagesReactive(util.List.of(messageId), FetchGroup.MINIMAL, session))
      .map(_.getFlags)
      .block

  private def createMessage(flags: Flags): MessageId = {
    val messageId: MessageId = messageIdManagerTestSystem.persist(mailbox.getMailboxId, MessageUid.of(111), flags, session)
    messageId
  }

  private def createFakeCalendaerEventBlobId(messageId: MessageId): BlobId =
    BlobId.of(BlobId.of(messageId).get, PartId.parse(Random.nextInt(1_000_000).toString).get).get

  protected def createTestSystem: MessageIdManagerTestSystem = {
    val messageIdFactory = new TestMessageId.Factory
    val resources = InMemoryIntegrationResources.builder()
      .preProvisionnedFakeAuthenticator()
      .fakeAuthorizator().inVmEventBus
      .defaultAnnotationLimits()
      .defaultMessageParser()
      .scanningSearchIndex()
      .noPreDeletionHooks()
      .storeQuotaManager()
      .build()
    new MessageIdManagerTestSystem(resources.getMessageIdManager, messageIdFactory, resources.getMailboxManager.getMapperFactory, resources.getMailboxManager)
  }

  private def done(blobId: BlobId, attendanceStatus: AttendanceStatus) =
    CalendarEventAttendanceResults.done(
      EventAttendanceStatusEntry(
        blobId.value.value,
        attendanceStatus))
}
