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
import org.apache.james.util.ReactorUtils;
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
    private final SpaceIndex index;
    private final TeamMailboxRepository teamMailboxRepository;
    private final DomainList domainList;
    private final MailingLists mailingLists;
    private final SpacePublisher publisher;
    private final SpaceConfiguration configuration;
    private final Clock clock;

    @Inject
    public SpaceReconciler(SpaceMailboxes spaceMailboxes, SpaceIndex index, TeamMailboxRepository teamMailboxRepository, DomainList domainList,
                           MailingLists mailingLists, SpacePublisher publisher, SpaceConfiguration configuration, Clock clock) {
        this.spaceMailboxes = spaceMailboxes;
        this.index = index;
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
                return index.get(snapshot.domain(), snapshot.spaceId())
                    .flatMap(teamMailbox -> settleIndexed(snapshot, teamMailbox, republish))
                    .defaultIfEmpty(false)
                    .flatMap(settled -> settled ? Mono.<Void>empty() : provision(snapshot, republish));
            });
    }

    /**
     * The index entry is written before the team mailbox is created, so a crash anywhere leaves an entry to resume
     * from: the team mailbox is created, adopted or reconciled at the name it gives. False when that name is unusable.
     */
    private Mono<Boolean> settleIndexed(SpaceSnapshot snapshot, TeamMailbox teamMailbox, boolean republish) {
        return readOptional(teamMailbox)
            .flatMap(existing -> existing
                .map(spaceMailbox -> settleExisting(snapshot, spaceMailbox, republish)
                    .switchIfEmpty(Mono.fromRunnable(() -> LOGGER.warn("The index names {} for space {}, which belongs to another space",
                        teamMailbox.asMailAddress().asString(), snapshot.spaceId()))))
                .orElseGet(() -> create(teamMailbox)
                    .flatMap(created -> bindNew(snapshot, created).thenReturn(true))
                    .defaultIfEmpty(false)));
    }

    /** True once handled, empty when the team mailbox belongs to another space. */
    private Mono<Boolean> settleExisting(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox, boolean republish) {
        Optional<String> boundTo = spaceMailbox.binding().map(SpaceBinding::spaceId);
        if (boundTo.isEmpty()) {
            return adopt(spaceMailbox)
                .then(bindNew(snapshot, spaceMailbox))
                .thenReturn(true);
        }
        if (boundTo.get().equals(snapshot.spaceId())) {
            return reconcile(spaceMailbox, snapshot, republish).thenReturn(true);
        }
        return Mono.empty();
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

    private Mono<Void> provision(SpaceSnapshot snapshot, boolean republish) {
        String name = SpaceNames.mailboxName(snapshot.name(), configuration.teamMailboxPrefix());
        return Flux.fromIterable(SpaceNames.candidates(name))
            .concatMap(candidate -> take(snapshot, candidate, republish))
            .next()
            .switchIfEmpty(Mono.error(() -> new SpaceProvisioningException("No free address for space " + snapshot.spaceId()
                + " from " + name + "@" + snapshot.domain().asString())))
            .then();
    }

    /**
     * True once the space holds the candidate address, empty when another space, a user, an alias, a group or a list
     * holds it.
     */
    private Mono<Boolean> take(SpaceSnapshot snapshot, String candidate, boolean republish) {
        Optional<TeamMailbox> maybeTeamMailbox = OptionConverters.toJava(TeamMailbox.fromJava(snapshot.domain(), candidate));
        if (maybeTeamMailbox.isEmpty()) {
            return Mono.empty();
        }
        TeamMailbox teamMailbox = maybeTeamMailbox.get();
        return readOptional(teamMailbox)
            .flatMap(existing -> existing
                // Bound to this space before it was indexed, or no space holds it, or another space does
                .map(spaceMailbox -> spaceMailbox.binding().filter(binding -> !binding.spaceId().equals(snapshot.spaceId())).isPresent()
                    ? Mono.<Boolean>empty()
                    : index.put(snapshot.domain(), snapshot.spaceId(), teamMailbox)
                        .then(settleExisting(snapshot, spaceMailbox, republish)))
                .orElseGet(() -> isMailingList(teamMailbox)
                    .flatMap(isList -> isList
                        ? Mono.<Boolean>empty()
                        : index.put(snapshot.domain(), snapshot.spaceId(), teamMailbox)
                            .then(create(teamMailbox))
                            .flatMap(created -> bindNew(snapshot, created).thenReturn(true)))));
    }

    private Mono<Void> bindNew(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox) {
        return spaceMailboxes.bind(spaceMailbox, snapshot.organizationId(), snapshot.spaceId())
            .doOnSuccess(any -> LOGGER.info("Space {} got the team mailbox {}", snapshot.spaceId(), spaceMailbox.teamMailbox().asMailAddress().asString()))
            .then(publisher.publishProvisioned(snapshot.organizationId(), snapshot.spaceId(), spaceMailbox.rootId()))
            .then(setMembers(spaceMailbox.teamMailbox(), snapshot.members()));
    }

    /**
     * A team mailbox no space holds is adopted: where there are spaces, team mailboxes are only created for them, so it
     * is what a crash between its creation and the writing of its annotations left. This completes the folders and
     * rights the crash may have left out.
     */
    private Mono<Void> adopt(SpaceMailbox spaceMailbox) {
        return Mono.from(teamMailboxRepository.createTeamMailbox(spaceMailbox.teamMailbox()));
    }

    /** The created team mailbox, or empty when a user, an alias or a group holds the address. */
    private Mono<SpaceMailbox> create(TeamMailbox teamMailbox) {
        return Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox))
            .then(spaceMailboxes.read(teamMailbox))
            .onErrorResume(TeamMailboxNameConflictException.class, e -> Mono.empty());
    }

    private Mono<Boolean> isMailingList(TeamMailbox teamMailbox) {
        return Mono.fromCallable(() -> mailingLists.exists(teamMailbox.asMailAddress()))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Optional<SpaceMailbox>> readOptional(TeamMailbox teamMailbox) {
        return spaceMailboxes.read(teamMailbox)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
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
        return domain.map(Flux::just).orElseGet(this::domains)
            .concatMap(candidate -> liveMailboxOf(candidate, spaceId))
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
        return index.list(domain)
            .filter(entry -> !listed.contains(entry.spaceId()))
            .concatMap(entry -> liveMailboxOf(domain, entry.spaceId()))
            .filter(spaceMailbox -> spaceMailbox.binding().map(SpaceBinding::organizationId).filter(organizationId::equals).isPresent())
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
        return liveMailboxOf(domain, spaceId)
            .switchIfEmpty(Mono.fromRunnable(() -> LOGGER.info("Skipping member event: space {} has no live team mailbox", spaceId)));
    }

    /** The live team mailbox of a space, through the index. */
    private Mono<SpaceMailbox> liveMailboxOf(Domain domain, String spaceId) {
        return index.get(domain, spaceId)
            .flatMap(spaceMailboxes::read)
            .filter(spaceMailbox -> spaceMailbox.isLiveFor(spaceId));
    }

    private Flux<Domain> domains() {
        return Mono.fromCallable(domainList::getDomains)
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapIterable(domains -> domains);
    }

    /** Makes a team mailbox the one of a space, then asks ldap-rest for the space so that its members are set. */
    public Mono<Void> bind(SpaceMailbox spaceMailbox, String organizationId, String spaceId) {
        return spaceMailboxes.bind(spaceMailbox, organizationId, spaceId)
            .then(spaceMailboxes.unmarkDeleted(spaceMailbox))
            .then(index.put(spaceMailbox.teamMailbox().domain(), spaceId, spaceMailbox.teamMailbox()))
            .then(publisher.requestSync(Optional.of(organizationId), Optional.of(spaceId)));
    }

    /** Indexes every team mailbox bound to a space, for the spaces bound before the index existed. */
    public Mono<Void> reindex(Runnable onIndexed) {
        return spaceMailboxes.listAll()
            .flatMap(spaceMailbox -> Mono.justOrEmpty(spaceMailbox.binding())
                .flatMap(binding -> index.put(spaceMailbox.teamMailbox().domain(), binding.spaceId(), spaceMailbox.teamMailbox()))
                .then(Mono.fromRunnable(() -> spaceMailbox.binding().ifPresent(any -> onIndexed.run()))), ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }

    public SpaceMailboxes spaceMailboxes() {
        return spaceMailboxes;
    }
}
