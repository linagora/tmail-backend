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

package com.linagora.tmail.james.jmap.identity

import java.nio.charset.StandardCharsets
import java.util.UUID

import com.google.common.hash.Hashing
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.events.Event.EventId
import org.apache.james.events.delivery.InVmEventDelivery
import org.apache.james.events.{InVMEventBus, MemoryEventDeadLetters, RetryBackoffConfiguration}
import org.apache.james.jmap.api.identity.{AllCustomIdentitiesDeleted, CustomIdentityCreated, CustomIdentityDeleted, CustomIdentityUpdated, IdentityEvent}
import org.apache.james.jmap.api.model.{AccountId, EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}
import org.apache.james.jmap.change.AccountIdRegistrationKey
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources
import org.apache.james.mailbox.model.{MailboxAnnotation, MailboxAnnotationKey, MailboxPath}
import org.apache.james.metrics.tests.RecordingMetricFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

class IdentityMetadataListenerTest {
  private val IDENTITIES_PREFIX = "/private/vendor/tmail/identities"
  private val ALICE: Username = Username.of("alice@domain.tld")

  private val IDENTITY_ID: IdentityId = IdentityId(UUID.fromString("2c9f1b12-b35a-43e6-9af2-0106fb53a943"))
  private val IDENTITY_ID_2: IdentityId = IdentityId(UUID.fromString("3d9f1b12-b35a-43e6-9af2-0106fb53a943"))

  private val IDENTITY: Identity = Identity(
    id = IDENTITY_ID,
    sortOrder = 100,
    name = IdentityName("Alice"),
    email = new MailAddress("alice@domain.tld"),
    replyTo = Some(List(EmailAddress(Some(EmailerName("Boss")), new MailAddress("boss@domain.tld")))),
    bcc = Some(List(EmailAddress(Some(EmailerName("Admin")), new MailAddress("admin@domain.tld")))),
    textSignature = TextSignature("Best regards"),
    htmlSignature = HtmlSignature("<b>Best regards</b>"),
    mayDelete = MayDeleteIdentity(true))

  private val IDENTITY_2: Identity = Identity(
    id = IDENTITY_ID_2,
    sortOrder = 200,
    name = IdentityName("Alice (alt)"),
    email = new MailAddress("alice@domain.tld"),
    replyTo = None,
    bcc = None,
    textSignature = TextSignature(""),
    htmlSignature = HtmlSignature(""),
    mayDelete = MayDeleteIdentity(false))

  private def keyHash(id: IdentityId): String =
    Hashing.murmur3_32_fixed().hashString(id.id.toString, StandardCharsets.UTF_8).toString

  private def annotationPrefix(id: IdentityId): String = s"$IDENTITIES_PREFIX/${keyHash(id)}"

  private val ID_1_PREFIX: String = annotationPrefix(IDENTITY_ID)
  private val ID_2_PREFIX: String = annotationPrefix(IDENTITY_ID_2)

  var resources: InMemoryIntegrationResources = _
  var jmapEventBus: InVMEventBus = _
  var listener: IdentityMetadataListener = _

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

    jmapEventBus = new InVMEventBus(
      new InVmEventDelivery(new RecordingMetricFactory()),
      RetryBackoffConfiguration.FAST,
      new MemoryEventDeadLetters())

    listener = new IdentityMetadataListener(mailboxManager, mapperFactory)
    jmapEventBus.register(listener, listener.getDefaultGroup)

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

  private def dispatch(event: IdentityEvent): Unit =
    SMono.fromPublisher(jmapEventBus.dispatch(event, AccountIdRegistrationKey(AccountId.fromUsername(ALICE)))).block()

  @Test
  def identityCreatedShouldWriteIdAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/id", IDENTITY_ID.id.toString))
  }

  @Test
  def identityCreatedShouldWriteSortOrderAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/sortorder", "100"))
  }

  @Test
  def identityCreatedShouldWriteDisplayNameAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/displayname", "Alice"))
  }

  @Test
  def identityCreatedShouldWriteHtmlSignatureAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/html", "<b>Best regards</b>"))
  }

  @Test
  def identityCreatedShouldWriteTextSignatureAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/text", "Best regards"))
  }

  @Test
  def identityCreatedShouldWriteMayDeleteAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/maydelete", "true"))
  }

  @Test
  def identityCreatedShouldWriteReplyToAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/replyto", "Boss <boss@domain.tld>"))
  }

  @Test
  def identityCreatedShouldWriteBccAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/bcc", "Admin <admin@domain.tld>"))
  }

  @Test
  def identityCreatedShouldNotWriteReplyToAnnotationWhenAbsent(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY_2))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(new MailboxAnnotationKey(s"$ID_2_PREFIX/replyto"))
  }

  @Test
  def identityCreatedShouldNotWriteBccAnnotationWhenAbsent(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY_2))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(new MailboxAnnotationKey(s"$ID_2_PREFIX/bcc"))
  }

  @Test
  def identityCreatedShouldWriteAnnotationsForMultipleIdentities(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY_2))

    assertThat(annotations.asJava)
      .contains(
        annotation(s"$ID_1_PREFIX/displayname", "Alice"),
        annotation(s"$ID_2_PREFIX/displayname", "Alice (alt)"))
  }

  @Test
  def identityUpdatedShouldUpdateDisplayNameAnnotation(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    val updated = IDENTITY.copy(name = IdentityName("Alice Updated"))
    dispatch(CustomIdentityUpdated(EventId.random(), ALICE, updated))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_1_PREFIX/displayname", "Alice Updated"))
  }

  @Test
  def identityUpdatedShouldRemoveReplyToAnnotationWhenCleared(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))

    val updated = IDENTITY.copy(replyTo = None)
    dispatch(CustomIdentityUpdated(EventId.random(), ALICE, updated))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(new MailboxAnnotationKey(s"$ID_1_PREFIX/replyto"))
  }

  @Test
  def identityDeletedShouldRemoveAllAnnotations(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))
    dispatch(CustomIdentityDeleted(EventId.random(), ALICE, Set(IDENTITY_ID)))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(
        new MailboxAnnotationKey(s"$ID_1_PREFIX/id"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/sortorder"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/displayname"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/html"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/text"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/maydelete"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/replyto"),
        new MailboxAnnotationKey(s"$ID_1_PREFIX/bcc"))
  }

  @Test
  def identityDeletedShouldOnlyRemoveAnnotationsForSpecifiedIdentity(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY_2))
    dispatch(CustomIdentityDeleted(EventId.random(), ALICE, Set(IDENTITY_ID)))

    assertThat(annotations.asJava)
      .contains(annotation(s"$ID_2_PREFIX/displayname", "Alice (alt)"))
  }

  @Test
  def allIdentitiesDeletedShouldRemoveAllAnnotationsForAllIdentities(): Unit = {
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY))
    dispatch(CustomIdentityCreated(EventId.random(), ALICE, IDENTITY_2))
    dispatch(AllCustomIdentitiesDeleted(EventId.random(), ALICE, Set(IDENTITY_ID, IDENTITY_ID_2)))

    assertThat(annotations.map(_.getKey).asJava)
      .doesNotContain(
        new MailboxAnnotationKey(s"$ID_1_PREFIX/displayname"),
        new MailboxAnnotationKey(s"$ID_2_PREFIX/displayname"))
  }

  private def annotation(key: String, value: String): MailboxAnnotation =
    MailboxAnnotation.newInstance(new MailboxAnnotationKey(key), value)
}
