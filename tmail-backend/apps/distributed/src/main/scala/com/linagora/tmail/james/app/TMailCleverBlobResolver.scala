/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * ****************************************************************** */

package com.linagora.tmail.james.app

import jakarta.inject.Inject
import org.apache.james.jmap.mail.BlobId
import org.apache.james.jmap.routes.{AttachmentBlobResolver, BlobResolutionResult, BlobResolver, MessagePartBlobResolver, NonApplicable}
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.SMono

class TMailCleverBlobResolver @Inject() (messagePartBlobResolver: MessagePartBlobResolver, attachmentBlobResolver: AttachmentBlobResolver) extends BlobResolver {
  override def resolve(blobId: BlobId, mailboxSession: MailboxSession): SMono[BlobResolutionResult] =
    attachmentBlobResolver.resolve(blobId, mailboxSession)
      .flatMap[BlobResolutionResult] {
      case NonApplicable => messagePartBlobResolver.resolve(blobId, mailboxSession)
      case any => SMono.just(any)
    }
}
