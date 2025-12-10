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

package com.linagora.tmail.listener.rag

import com.linagora.tmail.listener.rag.LlmMailPrioritizationClassifierListenerContract.ALICE
import org.apache.james.core.MailAddress
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, MayDeleteIdentity, TextSignature}

object IdentityFixture {
  val ALICE_SERVER_SET_IDENTITY: Identity = Identity(id = IdentityId.generate,
    name = IdentityName(""),
    email = ALICE.asMailAddress(),
    replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss 1")), new MailAddress("boss1@domain.tld")))),
    bcc = Some(List(EmailAddress(Some(EmailerName("My Boss bcc 1")), new MailAddress("boss_bcc_1@domain.tld")))),
    textSignature = TextSignature(""),
    htmlSignature = HtmlSignature(""),
    mayDelete = MayDeleteIdentity(false))
}
