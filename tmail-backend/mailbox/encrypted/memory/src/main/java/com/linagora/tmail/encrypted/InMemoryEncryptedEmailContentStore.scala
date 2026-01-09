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

package com.linagora.tmail.encrypted

import java.lang
import java.nio.charset.StandardCharsets
import com.google.common.base.Preconditions
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.linagora.tmail.encrypted.EncryptedEmailContentStore.POSITION_NUMBER_START_AT
import jakarta.inject.Inject
import org.apache.james.blob.api.BlobStore.StoragePolicy
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.mailbox.model.MessageId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

case class InMemoryEncryptedEmailContentStoreModule() extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[EncryptedEmailContentStore]).to(classOf[InMemoryEncryptedEmailContentStore])
  }

  @Provides
  @Singleton
  def provideInMemoryEncryptedEmailContentStore(blobStore: BlobStore): InMemoryEncryptedEmailContentStore =
    new InMemoryEncryptedEmailContentStore(blobStore)

}

class InMemoryEncryptedEmailContentStore @Inject()(blobStore: BlobStore,
                                                   emailContentStore: scala.collection.concurrent.Map[MessageId, EncryptedEmailContent] = scala.collection.concurrent.TrieMap(),
                                                   messageIdBlobIdStore: scala.collection.concurrent.Map[MessageId, Map[Int, BlobId]] = scala.collection.concurrent.TrieMap()) extends EncryptedEmailContentStore {

  def this(blobstoreImpl: BlobStore) = this(blobStore = blobstoreImpl)

  override def store(messageId: MessageId, encryptedEmailContent: EncryptedEmailContent): Publisher[Unit] =
    storeAttachment(messageId, encryptedEmailContent.encryptedAttachmentContents)
      .`then`(SMono.just(emailContentStore.put(messageId, encryptedEmailContent)))

  override def delete(messageId: MessageId): Publisher[Unit] =
    removeAttachment(messageId)
      .`then`(SMono.just(emailContentStore.remove(messageId)))

  override def retrieveFastView(messageId: MessageId): Publisher[EncryptedEmailFastView] =
    SMono.justOrEmpty(emailContentStore.get(messageId)
      .map(encryptedEmailContent => EncryptedEmailFastView.from(messageId, encryptedEmailContent)))
      .switchIfEmpty(SMono.error(MessageNotFoundException(messageId)))

  override def retrieveDetailedView(messageId: MessageId): Publisher[EncryptedEmailDetailedView] =
    SMono.justOrEmpty(emailContentStore.get(messageId)
      .map(encryptedEmailContent => EncryptedEmailDetailedView.from(messageId, encryptedEmailContent)))
      .switchIfEmpty(SMono.error(MessageNotFoundException(messageId)))

  override def retrieveAttachmentContent(messageId: MessageId, position: Int): Publisher[BlobId] =
    SMono.justOrEmpty(messageIdBlobIdStore.get(messageId)
      .flatMap(positionBlobIdMap => positionBlobIdMap.get(position)))
      .switchIfEmpty(SMono.error(AttachmentNotFoundException(messageId, position)))

  private def storeAttachment(messageId: MessageId, encryptedAttachmentContents: List[String]): SMono[Unit] = {
    Preconditions.checkNotNull(encryptedAttachmentContents)
    SFlux.fromIterable(encryptedAttachmentContents)
      .concatMap(attachmentContent => SMono.fromPublisher(blobStore.save(BucketName.DEFAULT, attachmentContent.getBytes(StandardCharsets.UTF_8), StoragePolicy.LOW_COST)))
      .index()
      .collectMap(positionBlobId => positionBlobId._1.intValue + POSITION_NUMBER_START_AT, positionBlobId => positionBlobId._2)
      .map(positionBlobIdMap => messageIdBlobIdStore.put(messageId, positionBlobIdMap))
      .`then`()
  }

  private def removeAttachment(messageId: MessageId): SMono[Unit] =
    deleteBlobStore(messageId)
      .`then`()
      .`then`(SMono.just(messageIdBlobIdStore.remove(messageId)))

  private def deleteBlobStore(messageId: MessageId): SFlux[lang.Boolean] =
    SFlux.fromIterable(messageIdBlobIdStore.getOrElse(messageId, Map()).values)
      .flatMap(blobId => SMono.fromPublisher(blobStore.delete(BucketName.DEFAULT, blobId)))
}
