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

package com.linagora.tmail.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;

import com.google.common.collect.ImmutableList;

public class TMailPlainSaslMechanism extends PlainSaslMechanism {
    private static final String DELEGATION_SPLIT_CHARACTER = "+";

    private static class InvalidDelegationSyntaxException extends IllegalArgumentException {
        private final Username username;

        private InvalidDelegationSyntaxException(Username username) {
            this.username = username;
        }

        private Username username() {
            return username;
        }
    }

    public TMailPlainSaslMechanism() {
        super();
    }

    public TMailPlainSaslMechanism(boolean enabled, boolean requiresSsl) {
        super(enabled, requiresSsl);
    }

    @Override
    public SaslStep authenticate(Username username, String password, SaslAuthenticator authenticator) {
        try {
            return verify(handleTwoTokens(username.asString(), password), authenticator);
        } catch (InvalidDelegationSyntaxException e) {
            return new SaslStep.Failure(SaslFailure.invalidCredentials(e.username(), Optional.empty(), "Invalid credentials"));
        } catch (IllegalArgumentException e) {
            return new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command."));
        }
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new TMailPlainSaslExchange(request.initialResponse(), authenticator);
    }

    private class TMailPlainSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final SaslAuthenticator authenticator;

        private TMailPlainSaslExchange(Optional<byte[]> initialResponse, SaslAuthenticator authenticator) {
            this.initialResponse = initialResponse;
            this.authenticator = authenticator;
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Challenge(Optional.empty()));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return authenticate(clientResponse);
        }

        @Override
        public void close() {
        }

        private SaslStep authenticate(byte[] clientResponse) {
            try {
                return parseCredentials(clientResponse)
                    .map(credentials -> verify(credentials, authenticator))
                    .orElseGet(() -> new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
            } catch (InvalidDelegationSyntaxException e) {
                return new SaslStep.Failure(SaslFailure.invalidCredentials(e.username(), Optional.empty(), "Invalid credentials"));
            } catch (IllegalArgumentException e) {
                return new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command."));
            }
        }
    }

    private Optional<PlainCredentials> parseCredentials(byte[] clientResponse) {
        ImmutableList<String> tokens = Arrays.stream(new String(clientResponse, StandardCharsets.UTF_8).split("\0", -1))
            .collect(ImmutableList.toImmutableList());

        // Preserve legacy SMTP compatibility: some clients send a trailing NUL after the password.
        if (tokens.size() == 4 && tokens.get(3).isEmpty()) {
            return parseCredentials(tokens.subList(0, 3));
        }
        return parseCredentials(tokens);
    }

    private Optional<PlainCredentials> parseCredentials(List<String> tokens) {
        return switch (tokens.size()) {
            case 2 -> Optional.of(handleTwoTokens(tokens.get(0), tokens.get(1)));
            case 3 -> Optional.of(handleThreeTokens(tokens.get(0), tokens.get(1), tokens.get(2)));
            default -> Optional.empty();
        };
    }

    private PlainCredentials handleThreeTokens(String authorizationToken, String authenticationToken, String password) {
        if (authorizationToken.isEmpty()) {
            return handleTwoTokens(authenticationToken, password);
        }
        return credentials(Optional.of(Username.of(authorizationToken)), Username.of(authenticationToken), password);
    }

    private PlainCredentials handleTwoTokens(String userToken, String password) {
        Username user = Username.of(userToken);
        String localPart = user.getLocalPart();
        if (!localPart.contains(DELEGATION_SPLIT_CHARACTER)) {
            return credentials(Optional.empty(), user, password);
        }

        String authenticationId = StringUtils.substringBefore(localPart, DELEGATION_SPLIT_CHARACTER);
        String authorizationId = StringUtils.substringAfter(localPart, DELEGATION_SPLIT_CHARACTER);
        if (StringUtils.isAnyEmpty(authenticationId, authorizationId)) {
            throw new InvalidDelegationSyntaxException(user);
        }
        Username authenticationUser = Username.of(authenticationId).withDefaultDomain(user.getDomainPart());
        Username authorizationUser = Username.of(authorizationId).withDefaultDomain(user.getDomainPart());
        return credentials(Optional.of(authorizationUser), authenticationUser, password);
    }
}
