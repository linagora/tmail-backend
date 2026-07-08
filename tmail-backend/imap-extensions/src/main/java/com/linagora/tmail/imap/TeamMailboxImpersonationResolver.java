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

import org.apache.james.core.Username;
import org.apache.james.mailbox.Authorizator;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

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
 * A configured server administrator may impersonate any team mailbox, regardless of membership. As
 * an administrator holds no ACL on the team mailbox, the resulting session runs as the team mailbox
 * {@link TeamMailbox#owner()} (the owner of the {@code #TeamMailbox} mailboxes) so that ownership,
 * rather than membership, grants full access.
 *
 * When the authorization identifier does not resolve to a team mailbox the user may impersonate,
 * this returns empty so that the caller falls back to the regular user-to-user delegation.
 */
public class TeamMailboxImpersonationResolver {
    private final TeamMailboxRepository teamMailboxRepository;
    private final UsersRepository usersRepository;

    @Inject
    public TeamMailboxImpersonationResolver(TeamMailboxRepository teamMailboxRepository, UsersRepository usersRepository) {
        this.teamMailboxRepository = teamMailboxRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Resolves the team mailbox impersonation carried by a SASL identity: present only for a {@code +}
     * delegation whose authorization id designates a team mailbox the authenticated user may impersonate
     * (a member/manager, or a server administrator).
     */
    public Optional<TeamMailboxImpersonation> resolveScope(SaslIdentity identity) {
        Username authenticationId = identity.authenticationId();
        if (authenticationId.equals(identity.authorizationId())) {
            return Optional.empty();
        }
        return teamMailbox(identity.authorizationId())
            .flatMap(teamMailbox -> sessionUser(authenticationId, teamMailbox)
                .map(sessionUser -> new TeamMailboxImpersonation(teamMailbox, sessionUser)));
    }

    /**
     * Exposes team mailbox impersonation as an {@link Authorizator} so that James' SASL flow authorizes
     * a member/manager or an administrator to act as a team mailbox. Returns {@code FORBIDDEN} (a neutral
     * verdict here) when the authorization id is not a team mailbox the user may impersonate, letting the
     * regular user-to-user delegation authorizator decide.
     */
    public Authorizator asAuthorizator() {
        return (userId, otherUserId) -> teamMailbox(otherUserId)
            .flatMap(teamMailbox -> sessionUser(userId, teamMailbox))
            .map(sessionUser -> Authorizator.AuthorizationState.ALLOWED)
            .orElse(Authorizator.AuthorizationState.FORBIDDEN);
    }

    private Optional<TeamMailbox> teamMailbox(Username authorizationId) {
        return authorizationId.getDomainPart()
            .flatMap(domain -> OptionConverters.toJava(TeamMailbox.fromJava(domain, authorizationId.getLocalPart())));
    }

    /**
     * The user the impersonating session should run as: the member himself when he belongs to the team
     * mailbox, the team mailbox owner when an administrator impersonates it, empty otherwise.
     */
    private Optional<Username> sessionUser(Username authenticationId, TeamMailbox teamMailbox) {
        if (isMember(authenticationId, teamMailbox)) {
            return Optional.of(authenticationId);
        }
        if (isAdministrator(authenticationId)) {
            return Optional.of(teamMailbox.owner());
        }
        return Optional.empty();
    }

    private boolean isMember(Username user, TeamMailbox teamMailbox) {
        return Boolean.TRUE.equals(Flux.from(teamMailboxRepository.listTeamMailboxes(user))
            .any(teamMailbox::equals)
            .block());
    }

    private boolean isAdministrator(Username user) {
        try {
            return usersRepository.isAdministrator(user);
        } catch (UsersRepositoryException e) {
            return false;
        }
    }
}
