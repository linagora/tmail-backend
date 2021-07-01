package com.linagora.tmail.encrypted

import java.lang
import java.nio.charset.StandardCharsets

import com.google.common.base.Preconditions
import com.linagora.tmail.encrypted.EncryptedEmailContentStore.POSITION_NUMBER_START_AT
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStore.{emailContentStore, messageIdBlobIdStore}
import javax.inject.Inject
import org.apache.james.blob.api.BlobStore.StoragePolicy
import org.apache.james.blob.api.{BlobId, BlobStore}
import org.apache.james.mailbox.model.MessageId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

object InMemoryEncryptedEmailContentStore {
  val emailContentStore: scala.collection.concurrent.Map[MessageId, EncryptedEmailContent] = scala.collection.concurrent.TrieMap()
  val messageIdBlobIdStore: scala.collection.concurrent.Map[MessageId, Map[Int, BlobId]] = scala.collection.concurrent.TrieMap()
}

class InMemoryEncryptedEmailContentStore @Inject()(blobStore: BlobStore,
                                                   storagePolicy: StoragePolicy) extends EncryptedEmailContentStore {

  override def store(messageId: MessageId, encryptedEmailContent: EncryptedEmailContent): Publisher[Unit] =
    storeAttachment(messageId, encryptedEmailContent.encryptedAttachmentContents)
      .`then`(SMono.just(emailContentStore.put(messageId, encryptedEmailContent)))

  override def delete(messageId: MessageId): Publisher[Unit] =
    removeAttachment(messageId)
      .`then`(SMono.just(emailContentStore.remove(messageId)))

  override def retrieveFastView(messageId: MessageId): Publisher[EncryptedEmailFastView] =
    SMono.justOrEmpty(emailContentStore.get(messageId)
      .map(encryptedEmailContent => EncryptedEmailFastView.from(encryptedEmailContent)))
      .switchIfEmpty(SMono.error(MessageNotFoundException(messageId)))

  override def retrieveDetailedView(messageId: MessageId): Publisher[EncryptedEmailDetailedView] =
    SMono.justOrEmpty(emailContentStore.get(messageId)
      .map(encryptedEmailContent => EncryptedEmailDetailedView.from(encryptedEmailContent)))
      .switchIfEmpty(SMono.error(MessageNotFoundException(messageId)))

  override def retrieveAttachmentContent(messageId: MessageId, position: Int): Publisher[BlobId] =
    SMono.justOrEmpty(messageIdBlobIdStore.get(messageId)
      .flatMap(positionBlobIdMap => positionBlobIdMap.get(position)))
      .switchIfEmpty(SMono.error(AttachmentNotFoundException(messageId, position)))

  private def storeAttachment(messageId: MessageId, encryptedAttachmentContents: List[String]): SMono[Unit] = {
    Preconditions.checkNotNull(encryptedAttachmentContents)
    SFlux.fromIterable(encryptedAttachmentContents)
      .concatMap(attachmentContent => SMono.fromPublisher(blobStore.save(blobStore.getDefaultBucketName, attachmentContent.getBytes(StandardCharsets.UTF_8), storagePolicy)))
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
      .flatMap(blobId => SMono.fromPublisher(blobStore.delete(blobStore.getDefaultBucketName, blobId)))
}
