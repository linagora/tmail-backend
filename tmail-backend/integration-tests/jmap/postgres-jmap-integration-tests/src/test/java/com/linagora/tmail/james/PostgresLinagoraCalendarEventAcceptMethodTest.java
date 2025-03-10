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

package com.linagora.tmail.james;

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION;

import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.DefaultEventInvitationParameterResolver;
import com.linagora.tmail.james.common.LinagoraCalendarEventAcceptMethodContract;

@ExtendWith(DefaultEventInvitationParameterResolver.class)
public class PostgresLinagoraCalendarEventAcceptMethodTest implements LinagoraCalendarEventAcceptMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION.apply(new DelegationProbeModule())
        .build();

    @Override
    public String randomBlobId() {
        return TmailJmapBase.randomBlobId();
    }
}