package com.linagora.tmail.james.jmap

import java.util
import java.util.Optional

import com.linagora.tmail.james.jmap.method.CalendarEventReplyPerformer
import jakarta.mail.Flags
import org.apache.james.jmap.mail.{BlobId, BlobIds, PartId}
import org.apache.james.mailbox.exception.MailboxException
import org.apache.james.mailbox.fixture.MailboxFixture
import org.apache.james.mailbox.fixture.MailboxFixture.ALICE
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{FetchGroup, Mailbox, MessageId, TestMessageId}
import org.apache.james.mailbox.store.MessageIdManagerTestSystem
import org.apache.james.mailbox.{MailboxSession, MailboxSessionUtil, MessageUid}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.mockito.Mockito.mock
import reactor.core.publisher.Mono

import scala.util.Random

class StandaloneEventAttendanceRepositoryTest {
  var calendarEventReplyPerformer: CalendarEventReplyPerformer = _
  var session: MailboxSession = _
  var testee: StandaloneEventAttendanceRepository = _
  var messageIdManagerTestSystem: MessageIdManagerTestSystem = _
  var mailbox: Mailbox = _

  @BeforeEach
  @throws[MailboxException]
  def setUp(): Unit = {
    messageIdManagerTestSystem = createTestSystem
    calendarEventReplyPerformer = mock(classOf[CalendarEventReplyPerformer])
    testee = new StandaloneEventAttendanceRepository(messageIdManagerTestSystem.getMessageIdManager, messageIdManagerTestSystem.getMailboxManager.getSessionProvider, calendarEventReplyPerformer, new TestMessageId.Factory)
    session = MailboxSessionUtil.create(ALICE)
    mailbox = messageIdManagerTestSystem.createMailbox(MailboxFixture.INBOX_ALICE, session)
  }

  @Test
  def givenAcceptedFlagIsLinkedToMailGetAttendanceStatusShouldReturnAccepted(): Unit = {
    val flags: Flags = new Flags("$accepted")
    val messageId: MessageId = createMessage(flags)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, messageId)).block)
      .isEqualTo(AttendanceStatus.Accepted)
  }

  @Test
  def givenRejectedFlagIsLinkedToMailGetAttendanceStatusShouldReturnDeclined(): Unit = {
    val flags: Flags = new Flags("$rejected")
    val messageId: MessageId = createMessage(flags)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, messageId)).block).isEqualTo(AttendanceStatus.Declined)
  }

  @Test
  def givenTentativelyAcceptedFlagIsLinkedToMailGetAttendanceStatusShouldReturnTentative(): Unit = {
    val flags: Flags = new Flags("$tentativelyaccepted")
    val messageId: MessageId = createMessage(flags)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, messageId)).block).isEqualTo(AttendanceStatus.Tentative)
  }

  // It should also print a warning message
  @Test
  def givenMoreThanEventAttendanceFlagIsLinkedToMailGetAttendanceStatusShouldReturnAny(): Unit = {
    val flags: Flags = new Flags("$rejected")
    flags.add("$accepted")
    val messageId: MessageId = createMessage(flags)


    assertThat(util.List.of(AttendanceStatus.Accepted, AttendanceStatus.Declined))
      .contains(Mono.from(testee.getAttendanceStatus(mailbox.getUser, messageId)).block)
  }

  @Test
  def getAttendanceStatusShouldFallbackToNeedsActionWhenNoFlagIsLinkedToMail(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, messageId)).block).isEqualTo(AttendanceStatus.NeedsAction)
  }

  @Test
  def getAttendanceStatusShouldFallbackToNeedsActionWhenNoEventAttendanceFlagIsLinkedToMail(): Unit = {
    val flags: Flags = new Flags(Flags.Flag.RECENT)
    val messageId: MessageId = createMessage(flags)
    assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser, messageId)).block).isEqualTo(AttendanceStatus.NeedsAction)
  }

  @Test
  def setAttendanceStatusShouldSetAcceptedFlag(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds: BlobIds = BlobIds(Seq(calendaerEventBlobId.value))

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Accepted, blobIds, Optional.empty)).block

    val updatedFlags: Flags = getFlags(messageId)

    assertThat(updatedFlags.contains("$accepted")).isTrue
  }

  @Test
  def setAttendanceStatusShouldSetDeclinedFlag(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds: BlobIds = BlobIds(Seq(calendaerEventBlobId.value))

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Declined, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$rejected")).isTrue
  }

  @Test
  def setAttendanceStatusShouldSetTentativeFlag(): Unit = {
    val flags: Flags = new Flags
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds: BlobIds = BlobIds(Seq(calendaerEventBlobId.value))

    Mono.from(testee.setAttendanceStatus(mailbox.getUser, AttendanceStatus.Tentative, blobIds, Optional.empty)).block
    val updatedFlags: Flags = getFlags(messageId)
    assertThat(updatedFlags.contains("$tentativelyaccepted")).isTrue
  }

  @Test
  def setAttendanceStatusShouldRemoveExistingEventAttendanceFlags(): Unit = {
    val flags: Flags = new Flags("$accepted")
    val messageId: MessageId = createMessage(flags)
    val calendaerEventBlobId: BlobId = createFakeCalendaerEventBlobId(messageId)
    val blobIds: BlobIds = BlobIds(Seq(calendaerEventBlobId.value))

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
    val blobIds: BlobIds = BlobIds(Seq(calendaerEventBlobId.value))

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
    val blobIds: BlobIds = BlobIds(Seq(calendaerEventBlobId.value))

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
}
