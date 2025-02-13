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
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.postgres.change.PostgresStateFactory;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;

public class PostgresLinagoraLabelChangesMethodTest implements LabelChangesMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapGuiceLabelModule())
        .build();

    @Override
    public State.Factory stateFactory() {
        return new PostgresStateFactory();
    }
}