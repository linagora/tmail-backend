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

package com.linagora.tmail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.james.core.ConnectionDescription;
import org.apache.james.core.ConnectionDescriptionSupplier;
import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;


public class TestConnectionDisconnector implements Disconnector, ConnectionDescriptionSupplier {

    public final ArrayList<Username> connectedUsers;

    public TestConnectionDisconnector(List<Username> connectedUsers) {
        this.connectedUsers = new ArrayList<>(connectedUsers);
    }

    @Override
    public Stream<ConnectionDescription> describeConnections() {
        return connectedUsers.stream()
            .map(TestConnectionDisconnector::connectionDescription);
    }

    @Override
    public void disconnect(Predicate<Username> username) {
        connectedUsers.removeIf(username);
    }

    public static ConnectionDescription connectionDescription(Username username) {
        return new ConnectionDescription(
            "test-protocol",
            "test-endpoint",
            Optional.empty(),
            Optional.empty(),
            true, true, true, true,
            Optional.of(username),
            Map.of());
    }

}
