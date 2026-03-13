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

import java.nio.charset.StandardCharsets

import com.google.common.hash.Hashing
import com.linagora.tmail.james.jmap.model.{Label, LabelId}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.EventListener.{ExecutionMode, ReactiveGroupEventListener}
import org.apache.james.events.{Event, Group}
import org.apache.james.mailbox.MailboxManager
import org.apache.james.mailbox.model.{MailboxAnnotation, MailboxAnnotationKey, MailboxPath}
import org.apache.james.mailbox.store.MailboxSessionMapperFactory
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

class LabelMetadataListenerGroup extends Group {}

object LabelMetadataListener {
  val LABELS_ANNOTATION_PREFIX: String = "/private/vendor/tmail/labels"
}

class LabelMetadataListener @Inject()(mailboxManager: MailboxManager,
                                      mapperFactory: MailboxSessionMapperFactory) extends ReactiveGroupEventListener {

  import LabelMetadataListener.LABELS_ANNOTATION_PREFIX

  override def getDefaultGroup: Group = new LabelMetadataListenerGroup()

  override def getExecutionMode: ExecutionMode = ExecutionMode.SYNCHRONOUS

  override def isHandling(event: Event): Boolean = event.isInstanceOf[TmailLabelEvent]

  override def reactiveEvent(event: Event): Publisher[Void] = event match {
    case e: LabelCreated => upsertLabelAnnotations(e.username, e.label)
    case e: LabelUpdated => upsertLabelAnnotations(e.username, e.updatedLabel)
    case e: LabelDestroyed => deleteLabelAnnotations(e.username, e.labelId)
    case _ => Mono.empty
  }

  private def upsertLabelAnnotations(username: Username, label: Label): SMono[Unit] = {
    val keyword = label.keyword.flagName
    val session = mailboxManager.createSystemSession(username)
    SMono.fromPublisher(mailboxManager.getMailboxReactive(MailboxPath.inbox(username), session))
      .map(_.getId)
      .flatMap { mailboxId =>
        val annotationMapper = mapperFactory.getAnnotationMapper(session)
        val writeKeyword = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(keywordKey(keyword), keyword)))
        val writeDisplayName = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(displayNameKey(keyword), label.displayName.value)))
        val writeColor = label.color match {
          case Some(color) => SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
            MailboxAnnotation.newInstance(colorKey(keyword), color.value)))
          case None => SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, colorKey(keyword)))
        }
        writeKeyword.`then`(writeDisplayName).`then`(writeColor)
      }
      .`then`()
  }

  private def deleteLabelAnnotations(username: Username, labelId: LabelId): SMono[Unit] = {
    val keyword = labelId.toKeyword.flagName
    val session = mailboxManager.createSystemSession(username)
    SMono.fromPublisher(mailboxManager.getMailboxReactive(MailboxPath.inbox(username), session))
      .map(_.getId)
      .flatMap { mailboxId =>
        val annotationMapper = mapperFactory.getAnnotationMapper(session)
        SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, keywordKey(keyword)))
          .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, displayNameKey(keyword))))
          .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, colorKey(keyword))))
      }
      .`then`()
  }

  private def keyHash(keyword: String): String =
    Hashing.murmur3_32_fixed().hashString(keyword, StandardCharsets.UTF_8).toString

  private def annotationPrefix(keyword: String): String =
    s"$LABELS_ANNOTATION_PREFIX/${keyHash(keyword)}"

  private def keywordKey(keyword: String): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(keyword)}/keyword")

  private def displayNameKey(keyword: String): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(keyword)}/displayname")

  private def colorKey(keyword: String): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(keyword)}/color")
}
