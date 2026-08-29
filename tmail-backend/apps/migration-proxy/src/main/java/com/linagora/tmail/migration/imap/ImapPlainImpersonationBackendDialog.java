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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.linagora.tmail.migration.core.AdminCredentials;
import com.linagora.tmail.migration.core.BackendDialog;

/**
 * Opens a backend session for a user whose password the proxy does not know - the case for Kerberos
 * authenticated clients. The proxy authenticates as the configured backend administrator and asks, in
 * the same SASL PLAIN exchange, to be authorized as the end user.
 *
 * <p>The initial client response is deliberately sent as a continuation rather than inlined in the
 * {@code AUTHENTICATE} command: SASL-IR (RFC 4959) is an optional backend capability, this is not.
 */
public class ImapPlainImpersonationBackendDialog implements BackendDialog {
    private static final String AUTHENTICATE_PLAIN = ImapBackendDialog.PROXY_TAG + " AUTHENTICATE PLAIN";
    private static final char SASL_SEPARATOR = '\0';

    private enum State {
        WAIT_GREETING,
        WAIT_CHALLENGE,
        WAIT_AUTHENTICATE
    }

    private final String initialResponse;
    private State state;

    public ImapPlainImpersonationBackendDialog(String username, AdminCredentials admin) {
        this.initialResponse = initialResponse(username, admin);
        this.state = State.WAIT_GREETING;
    }

    @Override
    public Action onLine(String line) {
        return switch (state) {
            case WAIT_GREETING -> onGreeting(line);
            case WAIT_CHALLENGE -> onChallenge(line);
            case WAIT_AUTHENTICATE -> onAuthenticateResult(line);
        };
    }

    private Action onGreeting(String line) {
        if (line.startsWith("* OK")) {
            state = State.WAIT_CHALLENGE;
            return Action.send(AUTHENTICATE_PLAIN);
        }
        return waitOrFail(line);
    }

    private Action onChallenge(String line) {
        if (line.startsWith("+")) {
            state = State.WAIT_AUTHENTICATE;
            return Action.send(initialResponse);
        }
        return waitOrFail(line);
    }

    private Action onAuthenticateResult(String line) {
        if (line.startsWith(ImapBackendDialog.PROXY_TAG + " OK")) {
            return Action.SUCCESS;
        }
        return waitOrFail(line);
    }

    /**
     * A backend is free to emit untagged chatter at any point of the exchange, so an untagged line is
     * never an answer: it just leaves the state machine where it stands. Anything else is a refusal.
     */
    private static Action waitOrFail(String line) {
        if (line.startsWith("* ")) {
            return Action.WAIT;
        }
        return Action.FAILURE;
    }

    private static String initialResponse(String username, AdminCredentials admin) {
        String payload = username + SASL_SEPARATOR + admin.username() + SASL_SEPARATOR + admin.password();
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
