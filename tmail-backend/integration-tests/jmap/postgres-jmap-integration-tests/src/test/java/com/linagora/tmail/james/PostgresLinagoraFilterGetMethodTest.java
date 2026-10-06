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

import org.apache.james.JamesServerExtension;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LinagoraFilterGetMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceCustomModule;

public class PostgresLinagoraFilterGetMethodTest implements LinagoraFilterGetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapGuiceCustomModule())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    @Override
    public String generateMailboxIdForUser() {
        return PostgresMailboxId.of("123e4567-e89b-12d3-a456-426614174000").asUuid().toString();
    }

}