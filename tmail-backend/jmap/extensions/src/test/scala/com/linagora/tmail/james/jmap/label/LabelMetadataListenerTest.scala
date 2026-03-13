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

package com.linagora.tmail.james.jmap.label

import com.google.common.hash.Hashing
import com.linagora.tmail.james.jmap.label.LabelRepositoryContract.ALICE
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelId}
import org.apache.james.events.Event.EventId
import org.apache.james.events.delivery.InVmEventDelivery
import org.apache.james.events.{InVMEventBus, MemoryEventDeadLetters, RetryBackoffConfiguration}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.change.AccountIdRegistrationKey
import org.apache.james.jmap.mail.Keyword
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{MailboxAnnotation, MailboxAnnotationKey, MailboxPath}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

class LabelMetadataListenerTest {
  private val LABELS_PREFIX = "/private/vendor/tmail/labels"
  private val RED = Color("#FF0000")
  private val BLUE = Color("#0000FF")
  private val WORK_KEYWORD = "labelkeywordwork"
  private val PERSONAL_KEYWORD = "labelkeywordpersonal"

  private val WORK_LABEL = Label(
    id = LabelId.fromKeyword(Keyword.of(WORK_KEYWORD).get),
    displayName = DisplayName("Work"),
    keyword = Keyword.of(WORK_KEYWORD).get,
    color = Some(RED),
    description = None)

  private val PERSONAL_LABEL = Label(
    id = LabelId.fromKeyword(Keyword.of(PERSONAL_KEYWORD).get),
    displayName = DisplayName("Personal"),
    keyword = Keyword.of(PERSONAL_KEYWORD).get,
    color = Some(BLUE),
    description = None)

  private def keyHash(keyword: String): String =
    Hashing.murmur3_32_fixed().hashString(keyword, StandardCharsets.UTF_8).toString

  private def annotationPrefix(keyword: String): String = s"$LABELS_PREFIX/${keyHash(keyword)}"

  private val WORK_PREFIX = annotationPrefix(WORK_KEYWORD)
  private val PERSONAL_PREFIX = annotationPrefix(PERSONAL_KEYWORD)

  var resources: InMemoryIntegrationResources = _
  var tmailEventBus: InVMEventBus = _
  var listener: LabelMetadataListener = _

  @BeforeEach
  def setUp(): Unit = {
    resources = InMemoryIntegrationResources.builder()
      .preProvisionnedFakeAuthenticator()
      .fakeAuthorizator()
      .inVmEventBus
      .defaultAnnotationLimits()
      .defaultMessageParser()
      .scanningSearchIndex()
      .noPreDeletionHooks()
      .storeQuotaManager()
      .build()

    val mailboxManager = resources.getMailboxManager
    val mapperFactory = mailboxManager.getMapperFactory

    tmailEventBus = new InVMEventBus(
      new InVmEventDelivery(new RecordingMetricFactory()),
      RetryBackoffConfiguration.FAST,
      new MemoryEventDeadLetters())

    listener = new LabelMetadataListener(mailboxManager, mapperFactory)
    tmailEventBus.register(listener, listener.getDefaultGroup)

    val session = mailboxManager.createSystemSession(ALICE)
    mailboxManager.createMailbox(MailboxPath.inbox(ALICE), session)
  }

  private def annotationMapper = {
    val session = resources.getMailboxManager.createSystemSession(ALICE)
    resources.getMailboxManager.getMapperFactory.getAnnotationMapper(session)
  }

  private def inboxId = {
    val session = resources.getMailboxManager.createSystemSession(ALICE)
    SMono.fromPublisher(resources.getMailboxManager.getMailboxReactive(MailboxPath.inbox(ALICE), session))
      .map(_.getId)
      .block()
  }

  private def annotations = annotationMapper.getAllAnnotations(inboxId).asScala.toList

  private def dispatch(event: TmailLabelEvent): Unit =
    SMono.fromPublisher(tmailEventBus.dispatch(event, AccountIdRegistrationKey(AccountId.fromUsername(ALICE)))).block()

  @Test
  def labelCreatedShouldWriteKeywordAnnotation(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))

    assertThat(annotations.asJava)
      .contains(annotation(s"$WORK_PREFIX/keyword", WORK_KEYWORD))
  }

  @Test
  def labelCreatedShouldWriteDisplayNameAnnotation(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))

    assertThat(annotations.asJava)
      .contains(annotation(s"$WORK_PREFIX/displayName", "Work"))
  }

  @Test
  def labelCreatedShouldWriteColorAnnotationWhenColorIsPresent(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))

    assertThat(annotations.asJava)
      .contains(annotation(s"$WORK_PREFIX/color", RED.value))
  }

  @Test
  def labelCreatedShouldNotWriteColorAnnotationWhenColorIsAbsent(): Unit = {
    val labelWithoutColor = WORK_LABEL.copy(color = None)
    dispatch(LabelCreated(EventId.random(), ALICE, labelWithoutColor))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(new MailboxAnnotationKey(s"$WORK_PREFIX/color"))
  }

  @Test
  def labelCreatedShouldWriteAnnotationsForMultipleLabels(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))
    dispatch(LabelCreated(EventId.random(), ALICE, PERSONAL_LABEL))

    assertThat(annotations.asJava)
      .contains(
        annotation(s"$WORK_PREFIX/displayName", "Work"),
        annotation(s"$PERSONAL_PREFIX/displayName", "Personal"))
  }

  @Test
  def labelUpdatedShouldUpdateDisplayNameAnnotation(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))

    val updatedLabel = WORK_LABEL.copy(displayName = DisplayName("Work updated"))
    dispatch(LabelUpdated(EventId.random(), ALICE, updatedLabel))

    assertThat(annotations.asJava)
      .contains(annotation(s"$WORK_PREFIX/displayName", "Work updated"))
  }

  @Test
  def labelUpdatedShouldUpdateColorAnnotation(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))

    val updatedLabel = WORK_LABEL.copy(color = Some(BLUE))
    dispatch(LabelUpdated(EventId.random(), ALICE, updatedLabel))

    assertThat(annotations.asJava)
      .contains(annotation(s"$WORK_PREFIX/color", BLUE.value))
  }

  @Test
  def labelUpdatedShouldRemoveColorAnnotationWhenColorIsCleared(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))

    val updatedLabel = WORK_LABEL.copy(color = None)
    dispatch(LabelUpdated(EventId.random(), ALICE, updatedLabel))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(new MailboxAnnotationKey(s"$WORK_PREFIX/color"))
  }

  @Test
  def labelDestroyedShouldRemoveAllAnnotations(): Unit = {
    dispatch(LabelCreated(EventId.random(), ALICE, WORK_LABEL))
    dispatch(LabelDestroyed(EventId.random(), ALICE, WORK_LABEL.id))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(
        new MailboxAnnotationKey(s"$WORK_PREFIX/keyword"),
        new MailboxAnnotationKey(s"$WORK_PREFIX/displayName"),
        new MailboxAnnotationKey(s"$WORK_PREFIX/color"))
  }

  private def annotation(key: String, value: String) =
    MailboxAnnotation.newInstance(new MailboxAnnotationKey(key), value)
}
