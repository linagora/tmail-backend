package com.linagora.tmail.encrypted.cassandra

import java.lang

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.encrypted.EncryptedEmailContentStore.POSITION_NUMBER_START_AT
import com.linagora.tmail.encrypted.cassandra.CassandraEncryptedEmailContentStore.DEFAULT_STORAGE_POLICY
import com.linagora.tmail.encrypted.cassandra.table.CassandraEncryptedEmailStoreModule
import com.linagora.tmail.encrypted.{AttachmentNotFoundException, EncryptedEmailContent, EncryptedEmailContentStore, EncryptedEmailDetailedView, EncryptedEmailFastView, MessageNotFoundException}
import javax.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.blob.api.BlobStore.StoragePolicy
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId
import org.apache.james.mailbox.model.MessageId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

case class EncryptedEmailContentStoreCassandraModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraEncryptedEmailContentStore]).in(Scopes.SINGLETON)

    bind(classOf[EncryptedEmailContentStore]).to(classOf[CassandraEncryptedEmailContentStore])

    Multibinder.newSetBinder(binder, classOf[CassandraModule])
      .addBinding()
      .toInstance(CassandraEncryptedEmailStoreModule.MODULE)
  }
}

object CassandraEncryptedEmailContentStore {
  val DEFAULT_STORAGE_POLICY: StoragePolicy = StoragePolicy.LOW_COST
}

class CassandraEncryptedEmailContentStore @Inject()(blobStore: BlobStore,
                                                    encryptedEmailDAO: CassandraEncryptedEmailDAO) extends EncryptedEmailContentStore {
  val bucketName: BucketName = blobStore.getDefaultBucketName

  override def store(messageId: MessageId, encryptedEmailContent: EncryptedEmailContent): Publisher[Unit] =
    SFlux.fromIterable(encryptedEmailContent.encryptedAttachmentContents)
      .concatMap(encryptedAttachmentContent => SMono.fromPublisher(blobStore.save(bucketName, encryptedAttachmentContent, DEFAULT_STORAGE_POLICY)))
      .index()
      .collectMap(positionBlobId => positionBlobId._1.intValue + POSITION_NUMBER_START_AT, positionBlobId => positionBlobId._2)
      .flatMap(positionBlobIdMap => encryptedEmailDAO.insert(messageId.asInstanceOf[CassandraMessageId],
        EncryptedEmailDetailedView.from(messageId, encryptedEmailContent),
        positionBlobIdMap))

  override def delete(messageId: MessageId): Publisher[Unit] =
    deleteBlobStore(messageId)
      .`then`()
      .`then`(encryptedEmailDAO.delete(messageId.asInstanceOf[CassandraMessageId]))

  override def retrieveFastView(messageId: MessageId): Publisher[EncryptedEmailFastView] =
    encryptedEmailDAO.get(messageId.asInstanceOf[CassandraMessageId])
      .map(encryptedEmailDetailedView => EncryptedEmailFastView.from(messageId, encryptedEmailDetailedView))
      .switchIfEmpty(SMono.error(MessageNotFoundException(messageId)))

  override def retrieveDetailedView(messageId: MessageId): Publisher[EncryptedEmailDetailedView] =
    encryptedEmailDAO.get(messageId.asInstanceOf[CassandraMessageId])
      .switchIfEmpty(SMono.error(MessageNotFoundException(messageId)))

  override def retrieveAttachmentContent(messageId: MessageId, position: Int): Publisher[BlobId] =
    encryptedEmailDAO.getBlobId(messageId.asInstanceOf[CassandraMessageId], position)
      .onErrorMap {
        case _: NullPointerException => AttachmentNotFoundException(messageId, position)
        case error => error
      }
      .switchIfEmpty(SMono.error(AttachmentNotFoundException(messageId, position)))

  private def deleteBlobStore(messageId: MessageId): SFlux[lang.Boolean] =
    encryptedEmailDAO.getBlobIds(messageId.asInstanceOf[CassandraMessageId])
      .flatMap(blobId => SMono.fromPublisher(blobStore.delete(bucketName, blobId)))
}
