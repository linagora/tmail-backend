package com.linagora.tmail.james.app

import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.{AttachmentBlobResolver, BlobResolutionResult, BlobResolver, MessagePartBlobResolver, NonApplicable}
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.SMono

class TMailCleverBlobResolver(messagePartBlobResolver: MessagePartBlobResolver, attachmentBlobResolver: AttachmentBlobResolver) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[BlobResolutionResult] =
    attachmentBlobResolver.resolve(blobId, mailboxSession)
      .flatMap[BlobResolutionResult] {
      case NonApplicable => messagePartBlobResolver.resolve(blobId, mailboxSession)
      case any => SMono.just(any)
    }
}
