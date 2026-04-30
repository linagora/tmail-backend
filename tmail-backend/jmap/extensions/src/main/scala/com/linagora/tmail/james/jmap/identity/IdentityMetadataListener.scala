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

import com.google.common.hash.Hashing
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.events.EventListener.{ExecutionMode, ReactiveGroupEventListener}
import org.apache.james.events.{Event, Group}
import org.apache.james.jmap.api.identity.{AllCustomIdentitiesDeleted, CustomIdentityCreated, CustomIdentityDeleted, CustomIdentityUpdated, IdentityEvent}
import org.apache.james.jmap.api.model.{EmailAddress, Identity, IdentityId}
import org.apache.james.lifecycle.api.Startable
import org.apache.james.mailbox.MailboxManager.CreateOption
import org.apache.james.mailbox.exception.{MailboxExistsException, MailboxNotFoundException}
import org.apache.james.mailbox.model.{MailboxAnnotation, MailboxAnnotationKey, MailboxId, MailboxPath}
import org.apache.james.mailbox.store.MailboxSessionMapperFactory
import org.apache.james.mailbox.{DefaultMailboxes, MailboxManager, MailboxSession}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class IdentityMetadataListenerGroup extends Group {}

object IdentityMetadataListener {
  val IDENTITIES_ANNOTATION_PREFIX: String = "/private/vendor/tmail/identities"
}

class IdentityMetadataListener @Inject()(mailboxManager: MailboxManager,
                                         mapperFactory: MailboxSessionMapperFactory) extends ReactiveGroupEventListener with Startable {

  import IdentityMetadataListener.IDENTITIES_ANNOTATION_PREFIX

  override def getDefaultGroup: Group = new IdentityMetadataListenerGroup()

  override def getExecutionMode: ExecutionMode = ExecutionMode.SYNCHRONOUS

  override def isHandling(event: Event): Boolean = event.isInstanceOf[IdentityEvent]

  override def reactiveEvent(event: Event): Publisher[Void] =
    event match {
      case e: CustomIdentityCreated => upsertIdentityAnnotations(e.username, e.identity)
      case e: CustomIdentityUpdated => upsertIdentityAnnotations(e.username, e.identity)
      case e: CustomIdentityDeleted => deleteIdentityAnnotations(e.username, e.identityIds)
      case e: AllCustomIdentitiesDeleted => deleteIdentityAnnotations(e.username, e.identityIds)
      case _ => SMono.empty[Unit]
    }

  private def getOrProvisionInbox(username: Username, session: MailboxSession): SMono[MailboxId] =
    SMono.fromPublisher(mailboxManager.getMailboxReactive(MailboxPath.inbox(username), session))
      .map(_.getId)
      .onErrorResume {
        case _: MailboxNotFoundException =>
          SFlux.fromIterable(DefaultMailboxes.DEFAULT_MAILBOXES.asScala)
            .concatMap(name =>
              SMono.fromPublisher(mailboxManager.createMailboxReactive(
                  MailboxPath.forUser(username, name),
                  CreateOption.CREATE_SUBSCRIPTION,
                  session))
                .onErrorResume { case _: MailboxExistsException => SMono.empty })
            .`then`()
            .`then`(SMono.fromPublisher(mailboxManager.getMailboxReactive(MailboxPath.inbox(username), session))
              .map(_.getId))
        case e => SMono.error(e)
      }

  private def upsertIdentityAnnotations(username: Username, identity: Identity): SMono[Unit] = {
    val session = mailboxManager.createSystemSession(username)
    getOrProvisionInbox(username, session)
      .flatMap { mailboxId =>
        val annotationMapper = mapperFactory.getAnnotationMapper(session)
        val id = identity.id

        val writeId = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(idKey(id), identity.id.id.toString)))
        val writeSortOrder = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(sortOrderKey(id), identity.sortOrder.toString)))
        val writeDisplayName = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(displayNameKey(id), identity.name.name)))
        val writeHtml = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(htmlKey(id), identity.htmlSignature.name)))
        val writeText = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(textKey(id), identity.textSignature.name)))
        val writeMayDelete = SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
          MailboxAnnotation.newInstance(mayDeleteKey(id), identity.mayDelete.value.toString)))
        val writeReplyTo = identity.replyTo match {
          case Some(list) => SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
            MailboxAnnotation.newInstance(replyToKey(id), formatEmailList(list))))
          case None => SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, replyToKey(id)))
        }
        val writeBcc = identity.bcc match {
          case Some(list) => SMono.fromPublisher(annotationMapper.insertAnnotationReactive(mailboxId,
            MailboxAnnotation.newInstance(bccKey(id), formatEmailList(list))))
          case None => SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, bccKey(id)))
        }

        writeId.`then`(writeSortOrder).`then`(writeDisplayName).`then`(writeHtml)
          .`then`(writeText).`then`(writeMayDelete).`then`(writeReplyTo).`then`(writeBcc)
      }
      .`then`()
  }

  private def deleteIdentityAnnotations(username: Username, identityIds: Set[IdentityId]): SMono[Unit] = {
    val session = mailboxManager.createSystemSession(username)
    getOrProvisionInbox(username, session)
      .flatMap { mailboxId =>
        val annotationMapper = mapperFactory.getAnnotationMapper(session)
        SFlux.fromIterable(identityIds)
          .flatMap { identityId =>
            SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, idKey(identityId)))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, sortOrderKey(identityId))))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, displayNameKey(identityId))))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, htmlKey(identityId))))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, textKey(identityId))))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, mayDeleteKey(identityId))))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, replyToKey(identityId))))
              .`then`(SMono.fromPublisher(annotationMapper.deleteAnnotationReactive(mailboxId, bccKey(identityId))))
          }
          .`then`()
      }
      .`then`()
  }

  private def keyHash(id: IdentityId): String =
    Hashing.murmur3_32_fixed().hashString(id.id.toString, StandardCharsets.UTF_8).toString

  private def annotationPrefix(id: IdentityId): String =
    s"$IDENTITIES_ANNOTATION_PREFIX/${keyHash(id)}"

  private def idKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/id")

  private def sortOrderKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/sortorder")

  private def displayNameKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/displayname")

  private def htmlKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/html")

  private def textKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/text")

  private def mayDeleteKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/maydelete")

  private def replyToKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/replyto")

  private def bccKey(id: IdentityId): MailboxAnnotationKey =
    new MailboxAnnotationKey(s"${annotationPrefix(id)}/bcc")

  private def formatEmailList(emails: List[EmailAddress]): String =
    emails.map(formatEmail).mkString(", ")

  private def formatEmail(email: EmailAddress): String =
    email.name match {
      case Some(name) => s"${name.value} <${email.email.asString}>"
      case None => s"<${email.email.asString}>"
    }
}
