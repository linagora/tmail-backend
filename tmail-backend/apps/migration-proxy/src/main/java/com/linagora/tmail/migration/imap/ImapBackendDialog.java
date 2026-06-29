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

package com.linagora.tmail.migration.imap;

import com.linagora.tmail.migration.core.BackendDialog;

/**
 * Authenticates against the backend IMAP server with a tagged {@code LOGIN}, on behalf of a client
 * that already authenticated against the proxy.
 */
public class ImapBackendDialog implements BackendDialog {
    public static final String PROXY_TAG = "PXY1";

    private enum State {
        WAIT_GREETING,
        WAIT_LOGIN
    }

    private final String loginCommand;
    private State state;

    public ImapBackendDialog(String username, String password) {
        this.loginCommand = PROXY_TAG + " LOGIN " + quote(username) + " " + quote(password);
        this.state = State.WAIT_GREETING;
    }

    @Override
    public Action onLine(String line) {
        return switch (state) {
            case WAIT_GREETING -> {
                if (line.startsWith("* OK")) {
                    state = State.WAIT_LOGIN;
                    yield Action.send(loginCommand);
                }
                if (line.startsWith("* ")) {
                    yield Action.WAIT;
                }
                yield Action.FAILURE;
            }
            case WAIT_LOGIN -> {
                if (line.startsWith("* ")) {
                    yield Action.WAIT;
                }
                if (line.startsWith(PROXY_TAG + " OK")) {
                    yield Action.SUCCESS;
                }
                yield Action.FAILURE;
            }
        };
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
