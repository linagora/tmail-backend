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
 *******************************************************************/

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.saas.api.DomainSendingValidator
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType}
import org.apache.james.jmap.method.EmailSubmissionSetValidation
import org.apache.mailet.Mail
import reactor.core.scala.publisher.SMono

class MailDnsConfigurationValidation @Inject()(domainSendingValidator: DomainSendingValidator) extends EmailSubmissionSetValidation {
  private val forbiddenToSend: SetErrorType = "forbiddenToSend"

  override def validate(mail: Mail): SMono[Option[SetError]] = {
    val authenticatedUser = mail.getAttribute(Mail.JMAP_AUTH_USER)
      .map(attribute => Username.of(attribute.getValue.getValue.toString))
    SMono(domainSendingValidator.canSend(authenticatedUser, mail.getMaybeSender))
      .map(allowed => if (allowed) None else Some(SetError(forbiddenToSend,
        SetErrorDescription(DomainSendingValidator.PENDING_ACTIVATION), None)))
  }
}
