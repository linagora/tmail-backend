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

package com.linagora.tmail.migration.core;

/**
 * Drives the minimal handshake the proxy performs against a backend on behalf of the client: it
 * consumes the backend responses line by line and decides what to send next, until it can tell
 * whether the authentication succeeded.
 *
 * <p>A fresh instance is created per relayed connection (it is stateful).
 */
public interface BackendDialog {
    enum Decision {
        SEND,
        WAIT,
        SUCCESS,
        FAILURE
    }

    record Action(Decision decision, String command) {
        public static Action send(String command) {
            return new Action(Decision.SEND, command);
        }

        public static final Action WAIT = new Action(Decision.WAIT, null);
        public static final Action SUCCESS = new Action(Decision.SUCCESS, null);
        public static final Action FAILURE = new Action(Decision.FAILURE, null);
    }

    /**
     * Reacts to a single complete line (delimiter stripped) received from the backend.
     */
    Action onLine(String line);
}
