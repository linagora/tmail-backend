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

package com.linagora.tmail.james.common.probe

import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.identity.{CustomIdentityDAO, IdentityCreationRequest}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, TextSignature}
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.SMono

class JmapGuiceIdentityProbe @Inject()(customIdentityDAO: CustomIdentityDAO) extends GuiceProbe {

  def addIdentity(username: Username, displayName: String): Identity =
    SMono.fromPublisher(customIdentityDAO.save(username, IdentityCreationRequest(
      name = Some(IdentityName(displayName)),
      email = username.asMailAddress(),
      replyTo = None,
      bcc = None,
      textSignature = Some(TextSignature("text signature")),
      htmlSignature = Some(HtmlSignature("<b>html signature</b>"))))).block()

  def addIdentityWithReplyTo(username: Username, displayName: String, replyToEmail: String): Identity =
    SMono.fromPublisher(customIdentityDAO.save(username, IdentityCreationRequest(
      name = Some(IdentityName(displayName)),
      email = username.asMailAddress(),
      replyTo = Some(List(EmailAddress(Some(EmailerName("Boss")), new MailAddress(replyToEmail)))),
      bcc = None,
      textSignature = Some(TextSignature("text signature")),
      htmlSignature = Some(HtmlSignature("<b>html signature</b>"))))).block()

  def deleteIdentity(username: Username, identityId: IdentityId): Unit =
    SMono.fromPublisher(customIdentityDAO.delete(username, Set(identityId))).block()
}
