package com.linagora.tmail.james.app

import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.{AttachmentBlobResolver, BlobResolutionResult, BlobResolver, MessagePartBlobResolver, NonApplicable}
import org.apache.james.mailbox.MailboxSession

class TMailCleverBlobResolver(messagePartBlobResolver: MessagePartBlobResolver, attachmentBlobResolver: AttachmentBlobResolver) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): BlobResolutionResult =
    attachmentBlobResolver.resolve(blobId, mailboxSession) match {
      case NonApplicable => messagePartBlobResolver.resolve(blobId, mailboxSession)
      case any => any
    }
}
