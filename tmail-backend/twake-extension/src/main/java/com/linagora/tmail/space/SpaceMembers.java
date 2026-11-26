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

package com.linagora.tmail.space;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMemberRole;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The members of a space's team mailbox. Admins become managers and editors members. Viewers get no access, team
 * mailboxes having no read only access. Members are matched by email, case insensitive.
 */
public class SpaceMembers {
    private static final TeamMemberRole MANAGER = TeamMemberRole.apply(TeamMemberRole.ManagerRole());
    private static final TeamMemberRole MEMBER = TeamMemberRole.apply(TeamMemberRole.MemberRole());

    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public SpaceMembers(TeamMailboxRepository teamMailboxRepository) {
        this.teamMailboxRepository = teamMailboxRepository;
    }

    /** Makes the members of the team mailbox those of the space, removing anyone else. */
    public Mono<Void> set(TeamMailbox teamMailbox, Collection<SpaceSnapshot.Member> members) {
        Map<Username, TeamMemberRole> wanted = wanted(members);
        return Flux.from(teamMailboxRepository.listMembers(teamMailbox))
            .collectMap(member -> normalize(member.username()))
            .flatMap(current -> Flux.concat(
                    Flux.fromIterable(current.entrySet())
                        .filter(entry -> !wanted.containsKey(entry.getKey()))
                        .concatMap(entry -> remove(teamMailbox, entry.getValue().username())),
                    Flux.fromIterable(wanted.entrySet())
                        .filter(entry -> !hasRole(current.get(entry.getKey()), entry.getValue()))
                        .concatMap(entry -> add(teamMailbox, entry.getKey(), entry.getValue())))
                .then());
    }

    /** Adds the named members, or changes their role. A viewer loses access. */
    public Mono<Void> change(TeamMailbox teamMailbox, Collection<SpaceSnapshot.Member> members) {
        return Flux.fromIterable(members)
            .filter(SpaceMembers::hasEmail)
            .concatMap(member -> {
                Username username = username(member);
                return teamRole(member.role())
                    .map(role -> add(teamMailbox, username, role))
                    .orElseGet(() -> remove(teamMailbox, username));
            })
            .then();
    }

    public Mono<Void> remove(TeamMailbox teamMailbox, Collection<SpaceSnapshot.Member> members) {
        return Flux.fromIterable(members)
            .filter(SpaceMembers::hasEmail)
            .concatMap(member -> remove(teamMailbox, username(member)))
            .then();
    }

    public Mono<Void> removeAll(TeamMailbox teamMailbox) {
        return Flux.from(teamMailboxRepository.listMembers(teamMailbox))
            .concatMap(member -> remove(teamMailbox, member.username()))
            .then();
    }

    private Mono<Void> add(TeamMailbox teamMailbox, Username username, TeamMemberRole role) {
        return Mono.from(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.of(username, role)));
    }

    private Mono<Void> remove(TeamMailbox teamMailbox, Username username) {
        return Mono.from(teamMailboxRepository.removeMember(teamMailbox, username));
    }

    private static boolean hasRole(TeamMailboxMember member, TeamMemberRole role) {
        return member != null && member.role().equals(role);
    }

    private static Map<Username, TeamMemberRole> wanted(Collection<SpaceSnapshot.Member> members) {
        return members.stream()
            .filter(SpaceMembers::hasEmail)
            .flatMap(member -> teamRole(member.role())
                .map(role -> Map.entry(username(member), role))
                .stream())
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue, SpaceMembers::strongest));
    }

    private static TeamMemberRole strongest(TeamMemberRole a, TeamMemberRole b) {
        return a.equals(MANAGER) ? a : b;
    }

    private static Optional<TeamMemberRole> teamRole(SpaceSnapshot.Role role) {
        return switch (role) {
            case ADMIN -> Optional.of(MANAGER);
            case EDITOR -> Optional.of(MEMBER);
            case VIEWER -> Optional.empty();
        };
    }

    private static boolean hasEmail(SpaceSnapshot.Member member) {
        return member.email() != null && !member.email().isBlank();
    }

    private static Username username(SpaceSnapshot.Member member) {
        return normalize(Username.of(member.email()));
    }

    private static Username normalize(Username username) {
        return Username.of(username.asString().toLowerCase(Locale.US));
    }
}
