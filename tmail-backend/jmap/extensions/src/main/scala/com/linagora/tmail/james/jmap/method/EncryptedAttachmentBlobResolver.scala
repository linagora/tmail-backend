package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.encrypted.{EncryptedAttachmentBlobId, EncryptedEmailContentStore}
import org.apache.james.blob.api.BlobStore
import org.apache.james.blob.api.BlobStore.StoragePolicy
import org.apache.james.jmap.api.model.Size
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.{Applicable, Blob, BlobResolutionResult, BlobResolver, NonApplicable}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.{ContentType, MessageId}
import reactor.core.scala.publisher.SMono

import java.io.{ByteArrayInputStream, InputStream}
import javax.inject.Inject
import scala.util.{Success, Try}

case class EncryptedAttachmentBlob(blobId: BlobId, bytes: Array[Byte]) extends Blob {
  override def contentType: ContentType = new ContentType("application/pgp")

  override def size: Try[Size] = Success(Size.sanitizeSize(bytes.length))

  override def content: InputStream = new ByteArrayInputStream(bytes)
}

class EncryptedAttachmentBlobResolver @Inject()(encryptedEmailContentStore: EncryptedEmailContentStore,
                                     messageIdFactory: MessageId.Factory,
                                     blobStore: BlobStore) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult =
    EncryptedAttachmentBlobId.parse(messageIdFactory, blobId.value.value)
      .fold(_ => NonApplicable,
        encryptedId => Applicable(
          SMono(encryptedEmailContentStore.retrieveAttachmentContent(encryptedId.messageId, encryptedId.position))
            .flatMap((blobStoreId: org.apache.james.blob.api.BlobId) =>
              SMono(blobStore.readBytes(blobStore.getDefaultBucketName, blobStoreId, StoragePolicy.LOW_COST)))
            .map(bytes => EncryptedAttachmentBlob(blobId, bytes))))
}
