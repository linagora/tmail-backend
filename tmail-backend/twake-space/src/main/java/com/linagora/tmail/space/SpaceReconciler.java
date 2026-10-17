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

import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxNameConflictException;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMemberRole;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import scala.jdk.javaapi.OptionConverters;

/**
 * Makes a space's team mailbox match the space. Every step is idempotent, so a redelivered or replayed event changes
 * nothing, and a crash is repaired by handling the event again.
 */
public class SpaceReconciler {
    @FunctionalInterface
    public interface MailingLists {
        MailingLists NONE = address -> false;

        boolean exists(MailAddress address);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SpaceReconciler.class);
    private static final TeamMemberRole MANAGER = TeamMemberRole.apply(TeamMemberRole.ManagerRole());
    private static final TeamMemberRole MEMBER = TeamMemberRole.apply(TeamMemberRole.MemberRole());

    private final SpaceMailboxes spaceMailboxes;
    private final TeamMailboxRepository teamMailboxRepository;
    private final DomainList domainList;
    private final MailingLists mailingLists;
    private final SpacePublisher publisher;
    private final SpaceConfiguration configuration;
    private final Clock clock;

    @Inject
    public SpaceReconciler(SpaceMailboxes spaceMailboxes, TeamMailboxRepository teamMailboxRepository, DomainList domainList,
                           MailingLists mailingLists, SpacePublisher publisher, SpaceConfiguration configuration, Clock clock) {
        this.spaceMailboxes = spaceMailboxes;
        this.teamMailboxRepository = teamMailboxRepository;
        this.domainList = domainList;
        this.mailingLists = mailingLists;
        this.publisher = publisher;
        this.configuration = configuration;
        this.clock = clock;
    }

    /**
     * Creates the team mailbox of a space that has none, and sets the members of a live one. A closed one stays
     * closed. {@code republish} sends the provisioned event again for a live mailbox, as a sync does.
     */
    public Mono<Void> apply(SpaceSnapshot snapshot, boolean republish) {
        return Mono.from(domainList.containsDomainReactive(snapshot.domain()))
            .flatMap(known -> {
                if (!known) {
                    // The DNS validation of the organization requests a sync, which brings the space back
                    LOGGER.info("Skipping space {}: domain {} is not a TMail domain yet", snapshot.spaceId(), snapshot.domain().asString());
                    return Mono.empty();
                }
                return spaceMailboxes.find(snapshot.domain(), snapshot.spaceId())
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty())
                    .flatMap(found -> found
                        .map(spaceMailbox -> reconcile(spaceMailbox, snapshot, republish))
                        .orElseGet(() -> provision(snapshot)));
            });
    }

    private Mono<Void> reconcile(SpaceMailbox spaceMailbox, SpaceSnapshot snapshot, boolean republish) {
        if (!spaceMailbox.isLive()) {
            LOGGER.info("Skipping space {}: its team mailbox {} is closed", snapshot.spaceId(), spaceMailbox.teamMailbox().asMailAddress().asString());
            return Mono.empty();
        }
        Mono<Void> provisioned = republish
            ? publisher.publishProvisioned(snapshot.organizationId(), snapshot.spaceId(), spaceMailbox.rootId())
            : Mono.empty();
        return provisioned.then(setMembers(spaceMailbox.teamMailbox(), snapshot.members()));
    }

    private Mono<Void> provision(SpaceSnapshot snapshot) {
        String name = SpaceNames.mailboxName(snapshot.name(), configuration.teamMailboxPrefix());
        return Flux.fromIterable(SpaceNames.candidates(name))
            .concatMap(candidate -> take(snapshot.domain(), candidate))
            .next()
            .switchIfEmpty(Mono.error(() -> new SpaceProvisioningException("No free address for space " + snapshot.spaceId()
                + " from " + name + "@" + snapshot.domain().asString())))
            .flatMap(spaceMailbox -> spaceMailboxes.bind(spaceMailbox, snapshot.organizationId(), snapshot.spaceId())
                .doOnSuccess(any -> LOGGER.info("Space {} got the team mailbox {}", snapshot.spaceId(), spaceMailbox.teamMailbox().asMailAddress().asString()))
                .then(publisher.publishProvisioned(snapshot.organizationId(), snapshot.spaceId(), spaceMailbox.rootId()))
                .then(setMembers(spaceMailbox.teamMailbox(), snapshot.members())));
    }

    /** The created team mailbox, or empty when the address is held by another space, a user, an alias, a group or a list. */
    private Mono<SpaceMailbox> take(Domain domain, String candidate) {
        Optional<TeamMailbox> maybeTeamMailbox = OptionConverters.toJava(TeamMailbox.fromJava(domain, candidate));
        if (maybeTeamMailbox.isEmpty()) {
            return Mono.empty();
        }
        TeamMailbox teamMailbox = maybeTeamMailbox.get();
        return spaceMailboxes.read(teamMailbox)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .flatMap(existing -> existing
                .map(spaceMailbox -> heldBy(spaceMailbox))
                .orElseGet(() -> create(teamMailbox)));
    }

    /**
     * A team mailbox of another space is taken. One no space holds is adopted: where there are spaces, team mailboxes
     * are only created for them, so it is what a crash between its creation and the writing of its annotations left.
     */
    private Mono<SpaceMailbox> heldBy(SpaceMailbox spaceMailbox) {
        if (spaceMailbox.binding().isPresent()) {
            return Mono.empty();
        }
        // Completes the folders and rights the crash may have left out
        return Mono.from(teamMailboxRepository.createTeamMailbox(spaceMailbox.teamMailbox()))
            .thenReturn(spaceMailbox);
    }

    private Mono<SpaceMailbox> create(TeamMailbox teamMailbox) {
        return Mono.fromCallable(() -> mailingLists.exists(teamMailbox.asMailAddress()))
            .subscribeOn(Schedulers.boundedElastic())
            .filter(isList -> !isList)
            .flatMap(any -> Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox))
                .then(spaceMailboxes.read(teamMailbox))
                .onErrorResume(TeamMailboxNameConflictException.class, e -> Mono.empty()));
    }

    /** Admins become managers and editors members. Viewers get no access, team mailboxes having no read only access. */
    public Mono<Void> setMembers(TeamMailbox teamMailbox, Collection<SpaceSnapshot.Member> members) {
        Map<Username, TeamMemberRole> wanted = wanted(members);
        return Flux.from(teamMailboxRepository.listMembers(teamMailbox))
            .collectMap(member -> normalize(member.username()))
            .flatMap(current -> Flux.concat(
                    Flux.fromIterable(current.entrySet())
                        .filter(entry -> !wanted.containsKey(entry.getKey()))
                        .concatMap(entry -> Mono.from(teamMailboxRepository.removeMember(teamMailbox, entry.getValue().username()))),
                    Flux.fromIterable(wanted.entrySet())
                        .filter(entry -> Optional.ofNullable(current.get(entry.getKey()))
                            .map(TeamMailboxMember::role)
                            .filter(entry.getValue()::equals)
                            .isEmpty())
                        .concatMap(entry -> Mono.from(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.of(entry.getKey(), entry.getValue())))))
                .then());
    }

    private Map<Username, TeamMemberRole> wanted(Collection<SpaceSnapshot.Member> members) {
        return members.stream()
            .filter(member -> member.email() != null && !member.email().isBlank())
            .flatMap(member -> teamRole(member.role())
                .map(role -> Map.entry(normalize(Username.of(member.email())), role))
                .stream())
            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue,
                (a, b) -> a.equals(MANAGER) ? a : b));
    }

    private Optional<TeamMemberRole> teamRole(SpaceSnapshot.Role role) {
        return switch (role) {
            case ADMIN -> Optional.of(MANAGER);
            case EDITOR -> Optional.of(MEMBER);
            case VIEWER -> Optional.empty();
        };
    }

    private Username normalize(Username username) {
        return Username.of(username.asString().toLowerCase(Locale.US));
    }

    /** A deleted space: its team mailbox loses its members, and is deleted after the retention. Its mail stays until then. */
    public Mono<Void> close(Optional<Domain> domain, String spaceId) {
        Flux<SpaceMailbox> candidates = domain.map(spaceMailboxes::list).orElseGet(spaceMailboxes::listAll);
        return candidates
            .filter(spaceMailbox -> spaceMailbox.isLiveFor(spaceId))
            .concatMap(this::close)
            .then();
    }

    private Mono<Void> close(SpaceMailbox spaceMailbox) {
        return Flux.from(teamMailboxRepository.listMembers(spaceMailbox.teamMailbox()))
            .concatMap(member -> Mono.from(teamMailboxRepository.removeMember(spaceMailbox.teamMailbox(), member.username())))
            .then(spaceMailboxes.markDeleted(spaceMailbox, clock.instant()))
            .doOnSuccess(any -> LOGGER.info("Closed the team mailbox {} of space {}", spaceMailbox.teamMailbox().asMailAddress().asString(),
                spaceMailbox.binding().map(SpaceBinding::spaceId).orElse("")));
    }

    /** After a snapshot of an organization: its live spaces the snapshot did not list are gone. */
    public Mono<Void> closeUnlisted(String organizationId, Domain domain, Collection<String> listedSpaceIds) {
        Set<String> listed = ImmutableSet.copyOf(listedSpaceIds);
        return spaceMailboxes.list(domain)
            .filter(spaceMailbox -> spaceMailbox.binding()
                .filter(SpaceBinding::isLive)
                .filter(binding -> binding.organizationId().equals(organizationId))
                .filter(binding -> !listed.contains(binding.spaceId()))
                .isPresent())
            .concatMap(this::close)
            .then();
    }

    /** Adds the named members, or changes their role. A viewer loses access. */
    public Mono<Void> changeMembers(Domain domain, String spaceId, Collection<SpaceSnapshot.Member> members) {
        return liveMailbox(domain, spaceId)
            .flatMapMany(spaceMailbox -> Flux.fromIterable(members)
                .filter(member -> member.email() != null && !member.email().isBlank())
                .concatMap(member -> {
                    Username username = normalize(Username.of(member.email()));
                    return teamRole(member.role())
                        .map(role -> Mono.from(teamMailboxRepository.addMember(spaceMailbox.teamMailbox(), TeamMailboxMember.of(username, role))))
                        .orElseGet(() -> Mono.from(teamMailboxRepository.removeMember(spaceMailbox.teamMailbox(), username)));
                }))
            .then();
    }

    public Mono<Void> removeMembers(Domain domain, String spaceId, Collection<SpaceSnapshot.Member> members) {
        return liveMailbox(domain, spaceId)
            .flatMapMany(spaceMailbox -> Flux.fromIterable(members)
                .filter(member -> member.email() != null && !member.email().isBlank())
                .concatMap(member -> Mono.from(teamMailboxRepository.removeMember(spaceMailbox.teamMailbox(), normalize(Username.of(member.email()))))))
            .then();
    }

    private Mono<SpaceMailbox> liveMailbox(Domain domain, String spaceId) {
        return spaceMailboxes.find(domain, spaceId)
            .filter(SpaceMailbox::isLive)
            .switchIfEmpty(Mono.fromRunnable(() -> LOGGER.info("Skipping member event: space {} has no live team mailbox", spaceId)));
    }

    /** Makes a team mailbox the one of a space, then asks ldap-rest for the space so that its members are set. */
    public Mono<Void> bind(SpaceMailbox spaceMailbox, String organizationId, String spaceId) {
        return spaceMailboxes.bind(spaceMailbox, organizationId, spaceId)
            .then(spaceMailboxes.unmarkDeleted(spaceMailbox))
            .then(publisher.requestSync(Optional.of(organizationId), Optional.of(spaceId)));
    }

    public SpaceMailboxes spaceMailboxes() {
        return spaceMailboxes;
    }
}
