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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.domainlist.api.DomainList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameConflictException;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Finds, or creates, the team mailbox of a space. The index entry of a new space is written before its team mailbox
 * is created, so a crash at any step leaves an entry to resume from, at the same address, even after a rename.
 *
 * <p>The team mailbox an entry names, bound to no space, is what a crash between its creation and the writing of its
 * annotations left: it is adopted, which completes the folders and rights the crash may have left out. Any other team
 * mailbox no space holds counts as taken.</p>
 */
public class SpaceProvisioner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpaceProvisioner.class);

    private final SpaceMailboxes spaceMailboxes;
    private final SpaceIndex index;
    private final TeamMailboxRepository teamMailboxRepository;
    private final SpaceAddresses addresses;
    private final DomainList domainList;

    @Inject
    public SpaceProvisioner(SpaceMailboxes spaceMailboxes, SpaceIndex index, TeamMailboxRepository teamMailboxRepository,
                            SpaceAddresses addresses, DomainList domainList) {
        this.spaceMailboxes = spaceMailboxes;
        this.index = index;
        this.teamMailboxRepository = teamMailboxRepository;
        this.addresses = addresses;
        this.domainList = domainList;
    }

    /**
     * The team mailbox of the space: bound to it, closed or not, or unbound when the space just got it. Empty when the
     * domain is not a TMail domain yet.
     */
    public Mono<SpaceMailbox> resolve(SpaceSnapshot snapshot) {
        return Mono.from(domainList.containsDomainReactive(snapshot.domain()))
            .flatMap(known -> {
                if (!known) {
                    // The DNS validation of the organization requests a sync, which brings the space back
                    LOGGER.info("Skipping space {}: domain {} is not a TMail domain yet", snapshot.spaceId(), snapshot.domain().asString());
                    return Mono.empty();
                }
                return index.get(snapshot.domain(), snapshot.spaceId())
                    .flatMap(teamMailbox -> fromIndex(snapshot, teamMailbox))
                    .switchIfEmpty(Mono.defer(() -> fromCandidates(snapshot)));
            });
    }

    private Mono<SpaceMailbox> fromIndex(SpaceSnapshot snapshot, TeamMailbox teamMailbox) {
        return readOptional(teamMailbox)
            .flatMap(existing -> existing
                .map(spaceMailbox -> ownedOrAdopted(snapshot, spaceMailbox)
                    .switchIfEmpty(Mono.fromRunnable(() -> LOGGER.warn("The index names {} for space {}, which belongs to another space",
                        teamMailbox.asMailAddress().asString(), snapshot.spaceId()))))
                .orElseGet(() -> create(teamMailbox)));
    }

    private Mono<SpaceMailbox> fromCandidates(SpaceSnapshot snapshot) {
        return Flux.fromIterable(addresses.candidates(snapshot.domain(), snapshot.name()))
            .concatMap(teamMailbox -> take(snapshot, teamMailbox))
            .next()
            .switchIfEmpty(Mono.error(() -> new SpaceProvisioningException("No free address for space " + snapshot.spaceId()
                + " named " + snapshot.name() + " in " + snapshot.domain().asString())));
    }

    /** Empty when another space, a user, an alias, a group or a mailing list holds the address. */
    private Mono<SpaceMailbox> take(SpaceSnapshot snapshot, TeamMailbox teamMailbox) {
        return readOptional(teamMailbox)
            .flatMap(existing -> existing
                .map(spaceMailbox -> claim(snapshot, spaceMailbox))
                .orElseGet(() -> claimFree(snapshot, teamMailbox)));
    }

    /**
     * A team mailbox at the address: only one bound to this space before it was indexed is taken. One bound to no space
     * may have been made by hand, or be a former space's kept for the purge: adopting it would hand its mail to the
     * members of this space. A crash before the binding leaves an index entry, which {@link #fromIndex} resumes.
     */
    private Mono<SpaceMailbox> claim(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox) {
        if (spaceMailbox.binding().isEmpty()) {
            LOGGER.warn("Space {} skips the address {}: a team mailbox bound to no space holds it", snapshot.spaceId(),
                spaceMailbox.teamMailbox().asMailAddress().asString());
            return Mono.empty();
        }
        if (isHeldByAnother(snapshot, spaceMailbox)) {
            return Mono.empty();
        }
        return index.put(snapshot.domain(), snapshot.spaceId(), spaceMailbox.teamMailbox())
            .thenReturn(spaceMailbox);
    }

    private Mono<SpaceMailbox> claimFree(SpaceSnapshot snapshot, TeamMailbox teamMailbox) {
        return addresses.isMailingList(teamMailbox)
            .flatMap(isList -> isList
                ? Mono.empty()
                : index.put(snapshot.domain(), snapshot.spaceId(), teamMailbox).then(create(teamMailbox)));
    }

    /** Empty when another space holds the team mailbox. */
    private Mono<SpaceMailbox> ownedOrAdopted(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox) {
        if (isHeldByAnother(snapshot, spaceMailbox)) {
            return Mono.empty();
        }
        if (spaceMailbox.binding().isPresent()) {
            return Mono.just(spaceMailbox);
        }
        return Mono.from(teamMailboxRepository.createTeamMailbox(spaceMailbox.teamMailbox()))
            .thenReturn(spaceMailbox);
    }

    private boolean isHeldByAnother(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox) {
        return spaceMailbox.binding()
            .map(SpaceBinding::spaceId)
            .filter(spaceId -> !spaceId.equals(snapshot.spaceId()))
            .isPresent();
    }

    /** Empty when a user, an alias or a group holds the address. */
    private Mono<SpaceMailbox> create(TeamMailbox teamMailbox) {
        return Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox))
            .then(spaceMailboxes.read(teamMailbox))
            .onErrorResume(TeamMailboxNameConflictException.class, e -> Mono.empty());
    }

    private Mono<Optional<SpaceMailbox>> readOptional(TeamMailbox teamMailbox) {
        return spaceMailboxes.read(teamMailbox)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
    }
}
