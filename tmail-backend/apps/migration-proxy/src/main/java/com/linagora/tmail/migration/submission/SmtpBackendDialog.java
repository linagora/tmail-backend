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


package com.linagora.tmail.migration.submission;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import com.linagora.tmail.migration.core.BackendDialog;

/**
 * Authenticates against the backend submission server on behalf of a client that already authenticated
 * against the proxy: waits for the greeting, sends {@code EHLO}, then replays the credentials with
 * {@code AUTH PLAIN} and its initial response.
 *
 * <p>Besides driving the handshake it records its {@link Outcome}, so that the proxy can tell the client
 * apart a password the backend rejected ({@code 535}) from a backend it could not use ({@code 454}).
 */
public class SmtpBackendDialog implements BackendDialog {
    public enum Outcome {
        /** The handshake did not reach a conclusion: the backend is unreachable, closed or timed out. */
        PENDING,
        AUTHENTICATED,
        /** The backend answered {@code 535}: the credentials are wrong. */
        CREDENTIALS_REJECTED,
        /** The backend could be reached but not used: unexpected, temporary or permanent error reply. */
        BACKEND_FAILURE
    }

    private enum State {
        AWAIT_GREETING,
        AWAIT_EHLO,
        AWAIT_AUTH,
        DONE
    }

    /**
     * A single line of an SMTP reply: the three digit code, and whether it ends the (multi-line) reply.
     */
    private record ReplyLine(int code, boolean last) {
        static Optional<ReplyLine> parse(String line) {
            if (line.length() < 3 || !isDigits(line.substring(0, 3))) {
                return Optional.empty();
            }
            if (line.length() == 3 || line.charAt(3) == ' ') {
                return Optional.of(new ReplyLine(Integer.parseInt(line.substring(0, 3)), true));
            }
            if (line.charAt(3) == '-') {
                return Optional.of(new ReplyLine(Integer.parseInt(line.substring(0, 3)), false));
            }
            return Optional.empty();
        }

        private static boolean isDigits(String value) {
            return value.chars().allMatch(Character::isDigit);
        }
    }

    private static final int SERVICE_READY = 220;
    private static final int OK = 250;
    private static final int AUTHENTICATION_SUCCEEDED = 235;
    private static final int AUTHENTICATION_CREDENTIALS_INVALID = 535;

    private final String ehloCommand;
    private final String authCommand;
    private State state;
    private Outcome outcome;

    public SmtpBackendDialog(String heloName, String username, String password) {
        this.ehloCommand = "EHLO " + heloName;
        this.authCommand = "AUTH PLAIN " + plainInitialResponse(username, password);
        this.state = State.AWAIT_GREETING;
        this.outcome = Outcome.PENDING;
    }

    @Override
    public Action onLine(String line) {
        Optional<ReplyLine> reply = ReplyLine.parse(line);
        if (reply.isEmpty()) {
            return fail(Outcome.BACKEND_FAILURE);
        }
        if (!reply.get().last()) {
            // The decision is taken on the last line of a multi-line reply, which repeats the code.
            return Action.WAIT;
        }
        int code = reply.get().code();
        return switch (state) {
            case AWAIT_GREETING -> expect(code, SERVICE_READY, State.AWAIT_EHLO, ehloCommand);
            case AWAIT_EHLO -> expect(code, OK, State.AWAIT_AUTH, authCommand);
            case AWAIT_AUTH -> onAuthReply(code);
            case DONE -> fail(Outcome.BACKEND_FAILURE);
        };
    }

    public Outcome outcome() {
        return outcome;
    }

    private Action expect(int code, int expected, State next, String command) {
        if (code != expected) {
            return fail(Outcome.BACKEND_FAILURE);
        }
        state = next;
        return Action.send(command);
    }

    private Action onAuthReply(int code) {
        if (code == AUTHENTICATION_SUCCEEDED) {
            state = State.DONE;
            outcome = Outcome.AUTHENTICATED;
            return Action.SUCCESS;
        }
        if (code == AUTHENTICATION_CREDENTIALS_INVALID) {
            return fail(Outcome.CREDENTIALS_REJECTED);
        }
        // 334 (the backend wants a challenge round we never asked for), 454, 504, 534, 538...: nothing the
        // client could fix by typing its password again.
        return fail(Outcome.BACKEND_FAILURE);
    }

    private Action fail(Outcome failure) {
        state = State.DONE;
        outcome = failure;
        return Action.FAILURE;
    }

    private static String plainInitialResponse(String username, String password) {
        String response = '\0' + username + '\0' + password;
        return Base64.getEncoder().encodeToString(response.getBytes(StandardCharsets.UTF_8));
    }
}
