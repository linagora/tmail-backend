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

package com.linagora.tmail.james.jmap.method

import java.io.{ByteArrayInputStream, InputStream}
import com.linagora.tmail.encrypted.{EncryptedAttachmentBlobId, EncryptedEmailContentStore}
import jakarta.inject.Inject
import org.apache.james.blob.api.{BlobStore, BucketName}
import org.apache.james.blob.api.BlobStore.StoragePolicy
import org.apache.james.jmap.api.model.Size
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.{Applicable, Blob, BlobResolutionResult, BlobResolver, NonApplicable}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.{ContentType, MessageId}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.util.{Success, Try}

case class EncryptedAttachmentBlob(blobId: BlobId, bytes: Array[Byte]) extends Blob {
  override def contentType: ContentType = ContentType.of("application/pgp")

  override def size: Try[Size] = Success(Size.sanitizeSize(bytes.length))

  override def content: InputStream = new ByteArrayInputStream(bytes)
}

class EncryptedAttachmentBlobResolver @Inject()(encryptedEmailContentStore: EncryptedEmailContentStore,
                                     messageIdFactory: MessageId.Factory,
                                     blobStore: BlobStore) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): Publisher[BlobResolutionResult] =
    EncryptedAttachmentBlobId.parse(messageIdFactory, blobId.value.value)
      .fold(_ =>  SMono.just(NonApplicable),
        encryptedId => SMono.just(Applicable(
          SMono(encryptedEmailContentStore.retrieveAttachmentContent(encryptedId.messageId, encryptedId.position))
            .flatMap((blobStoreId: org.apache.james.blob.api.BlobId) =>
              SMono(blobStore.readBytes(BucketName.DEFAULT, blobStoreId, StoragePolicy.LOW_COST)))
            .map(bytes => EncryptedAttachmentBlob(blobId, bytes)))))
}
