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

package com.linagora.tmail.james.common

import org.apache.james.jmap.rfc8621.contract.Fixture.{ALICE, ALICE_PASSWORD, ANDRE, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC}
import org.junit.jupiter.api.extension.{ExtensionContext, ParameterContext, ParameterResolver}

class DefaultEventInvitationParameterResolver extends ParameterResolver {

  override def supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.getParameter.getType eq classOf[EventInvitation]

  override def resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): AnyRef =
    EventInvitation(
      sender = User("ALICE", ALICE.asString(), ALICE_PASSWORD),
      senderTwo = User("CEDRIC", CEDRIC.asString(), "cedricpassword"),
      receiver = User("BOB", BOB.asString(), BOB_PASSWORD),
      joker = User("ANDRE", ANDRE.asString(), ANDRE_PASSWORD))
}
