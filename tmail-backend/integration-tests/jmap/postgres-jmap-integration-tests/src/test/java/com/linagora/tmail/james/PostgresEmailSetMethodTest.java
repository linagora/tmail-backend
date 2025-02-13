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

import static com.linagora.tmail.james.TmailJmapBase.JAMES_SERVER_EXTENSION_SUPPLIER;
import static com.linagora.tmail.james.TmailJmapBase.MESSAGE_ID_FACTORY;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.EmailSetMethodContract;
import org.apache.james.mailbox.model.MessageId;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresEmailSetMethodTest implements EmailSetMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = JAMES_SERVER_EXTENSION_SUPPLIER.get().build();

    @Override
    public MessageId randomMessageId() {
        return MESSAGE_ID_FACTORY.generate();
    }

    @Override
    public String invalidMessageIdMessage(String invalid) {
        return String.format("Invalid UUID string: %s", invalid);
    }

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Email/set call")
    public void newStateShouldBeUpToDate(GuiceJamesServer server) {}

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Email/set call")
    public void oldStateShouldIncludeSetChanges(GuiceJamesServer server) {}
}