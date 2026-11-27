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

import java.io.{FilterInputStream, InputStream}
import java.util.concurrent.atomic.AtomicLong

import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.{Blob, BlobResolver, BlobResolvers}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.ContentType
import reactor.core.scala.publisher.SMono

import scala.util.Try

case class AttachmentsTooLargeException(maximumSize: Long) extends RuntimeException(
  s"Attachments of this email exceed the maximum allowed total size of $maximumSize bytes (maxSizeAttachmentsPerEmail)")

case class TooManyAttachmentsException(actualCount: Int, maximumCount: Int) extends RuntimeException(
  s"This email has $actualCount attachments while the maximum allowed is $maximumCount")

object AttachmentsSizeBudget {
  val MAX_ATTACHMENTS_PER_MAIL: Int = 256
}

/**
 * Blob resolution for one Email/send creation, keeping a running total of the blobs it embeds (attachments,
 * every reference counted, and blob based text/html bodies).
 *
 * The size advertised by a blob is checked before its content is opened, and the content is then read bounded
 * by the remaining budget, so that a wrong advertised size does not make us read more than the limit.
 */
class AttachmentsSizeBudget(delegate: BlobResolvers, maximumSize: Long) extends BlobResolvers(Set.empty[BlobResolver]) {
  private val consumed: AtomicLong = new AtomicLong(0L)

  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[Blob] =
    delegate.resolve(blobId, mailboxSession)
      .map(blob => new BudgetedBlob(blob))

  override def validateAccess(blobId: BlobId, mailboxSession: MailboxSession): SMono[java.lang.Boolean] =
    delegate.validateAccess(blobId, mailboxSession)

  private def ensureFits(total: Long): Unit =
    if (total > maximumSize) {
      throw AttachmentsTooLargeException(maximumSize)
    }

  private class BudgetedBlob(blob: Blob) extends Blob {
    override def blobId: BlobId = blob.blobId

    override def contentType: ContentType = blob.contentType

    override def size: Try[Size] = blob.size

    override def content: InputStream = {
      blob.size.toOption.foreach(size => ensureFits(consumed.get() + size.value))
      new BudgetedInputStream(blob.content)
    }
  }

  private class BudgetedInputStream(in: InputStream) extends FilterInputStream(in) {
    override def read(): Int = {
      val value: Int = super.read()
      if (value >= 0) {
        ensureFits(consumed.incrementAndGet())
      }
      value
    }

    override def read(b: Array[Byte], off: Int, len: Int): Int = {
      val remaining: Long = Math.max(0L, maximumSize - consumed.get())
      val count: Int = super.read(b, off, Math.min(len.toLong, remaining + 1).toInt)
      if (count > 0) {
        ensureFits(consumed.addAndGet(count))
      }
      count
    }
  }
}
