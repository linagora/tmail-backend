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

package com.linagora.tmail.imap;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.protocols.api.sasl.SaslIdentity;

import com.google.inject.Inject;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import scala.jdk.javaapi.OptionConverters;

/**
 * Resolves whether an authorization identifier designates a team mailbox the authenticated user is
 * allowed to impersonate.
 *
 * A login of the form {@code member+teamMailbox@domain} is understood as "scope my session to the
 * team mailbox {@code teamMailbox@domain}" when such a team mailbox exists and {@code member} is one
 * of its members (or managers). This gives members and managers the right to impersonate a team
 * mailbox, as discussed in <a href="https://github.com/linagora/tmail-backend/issues/2405">#2405</a>.
 *
 * When the authorization identifier does not resolve to a team mailbox the user belongs to, this
 * returns empty so that the caller falls back to the regular user-to-user delegation.
 */
public class TeamMailboxImpersonationResolver {
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public TeamMailboxImpersonationResolver(TeamMailboxRepository teamMailboxRepository) {
        this.teamMailboxRepository = teamMailboxRepository;
    }

    public Optional<TeamMailbox> resolve(Username authenticationId, Username authorizationId) {
        Optional<Domain> domain = authorizationId.getDomainPart();
        if (domain.isEmpty()) {
            return Optional.empty();
        }
        return OptionConverters.toJava(TeamMailbox.fromJava(domain.get(), authorizationId.getLocalPart()))
            .filter(teamMailbox -> isMember(authenticationId, teamMailbox));
    }

    /**
     * Resolves the team mailbox scope carried by a SASL identity: present only for a {@code +}
     * delegation whose authorization id designates a team mailbox the authenticated user belongs to.
     */
    public Optional<TeamMailbox> resolveScope(SaslIdentity identity) {
        if (identity.authenticationId().equals(identity.authorizationId())) {
            return Optional.empty();
        }
        return resolve(identity.authenticationId(), identity.authorizationId());
    }

    /**
     * Exposes team mailbox impersonation as an {@link Authorizator} so that James' SASL flow authorizes
     * a member/manager to act as a team mailbox. Returns {@code FORBIDDEN} (a neutral verdict here) when
     * the authorization id is not a team mailbox the user belongs to, letting the regular user-to-user
     * delegation authorizator decide.
     */
    public Authorizator asAuthorizator() {
        return (userId, otherUserId) -> resolve(userId, otherUserId)
            .map(teamMailbox -> Authorizator.AuthorizationState.ALLOWED)
            .orElse(Authorizator.AuthorizationState.FORBIDDEN);
    }

    private boolean isMember(Username user, TeamMailbox teamMailbox) {
        return Boolean.TRUE.equals(Flux.from(teamMailboxRepository.listTeamMailboxes(user))
            .any(teamMailbox::equals)
            .block());
    }
}
