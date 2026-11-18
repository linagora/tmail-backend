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

import jakarta.inject.Inject
import org.apache.james.jmap.method.{EmailSubmissionSetValidation, EmailSubmissionSetValidationException}
import org.apache.james.lifecycle.api.LifecycleUtil
import org.apache.james.mailbox.MailboxSession
import org.apache.mailet.Mail
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._

/**
 * Runs the configured `EmailSubmissionSetValidation`s (`send.validate.rcpt`, `send.extra.validations`...)
 * on the TMail paths that spool mails on their own, bypassing `EmailSubmission/set`.
 *
 * The first rejection wins and fails with an `EmailSubmissionSetValidationException`. As the mail holds the
 * message content, it is disposed of whenever it does not reach the queue.
 */
class SubmissionValidations @Inject()(javaValidations: java.util.Set[EmailSubmissionSetValidation]) {
  private val validations: Seq[EmailSubmissionSetValidation] = javaValidations.asScala.toSeq

  def apply(mail: Mail, mailboxSession: MailboxSession): SMono[Mail] =
    SFlux.fromIterable(validations)
      .concatMap(_.validate(mail, mailboxSession))
      .filter(_.isDefined)
      .map(_.get)
      .next()
      .flatMap[Mail](setError => SMono.error(EmailSubmissionSetValidationException(setError)))
      .switchIfEmpty(SMono.just(mail))
      .onErrorResume(e => SMono.fromCallable(() => LifecycleUtil.dispose(mail))
        .subscribeOn(Schedulers.boundedElastic())
        .`then`(SMono.error(e)))
}
