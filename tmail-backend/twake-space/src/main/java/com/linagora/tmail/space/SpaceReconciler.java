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
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Makes a space's team mailbox match the space. Every step is idempotent, so a redelivered or replayed event changes
 * nothing, and a crash is repaired by handling the event again.
 */
public class SpaceReconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpaceReconciler.class);

    private final SpaceProvisioner provisioner;
    private final SpaceMailboxes spaceMailboxes;
    private final SpaceIndex index;
    private final SpaceMembers members;
    private final SpacePublisher publisher;

    @Inject
    public SpaceReconciler(SpaceProvisioner provisioner, SpaceMailboxes spaceMailboxes, SpaceIndex index, SpaceMembers members,
                           SpacePublisher publisher) {
        this.provisioner = provisioner;
        this.spaceMailboxes = spaceMailboxes;
        this.index = index;
        this.members = members;
        this.publisher = publisher;
    }

    /**
     * Creates the team mailbox of a space that has none, and sets the members of a live one. A closed one stays
     * closed. {@code republish} sends the provisioned event again for a live mailbox, as a sync does.
     */
    public Mono<Void> apply(SpaceSnapshot snapshot, boolean republish) {
        return provisioner.resolve(snapshot)
            .flatMap(spaceMailbox -> spaceMailbox.binding().isEmpty()
                ? bindNew(snapshot, spaceMailbox)
                : reconcile(snapshot, spaceMailbox, republish));
    }

    private Mono<Void> bindNew(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox) {
        return spaceMailboxes.bind(spaceMailbox, snapshot.ref())
            .doOnSuccess(any -> LOGGER.info("Space {} got the team mailbox {}", snapshot.spaceId(), address(spaceMailbox)))
            .then(publisher.publishProvisioned(snapshot.ref(), spaceMailbox.rootId()))
            .then(members.set(spaceMailbox.teamMailbox(), snapshot.members()));
    }

    private Mono<Void> reconcile(SpaceSnapshot snapshot, SpaceMailbox spaceMailbox, boolean republish) {
        if (!spaceMailbox.isLive()) {
            LOGGER.info("Skipping space {}: its team mailbox {} is closed", snapshot.spaceId(), address(spaceMailbox));
            return Mono.empty();
        }
        Mono<Void> provisioned = republish
            ? publisher.publishProvisioned(snapshot.ref(), spaceMailbox.rootId())
            : Mono.empty();
        return provisioned.then(members.set(spaceMailbox.teamMailbox(), snapshot.members()));
    }

    /** Adds the named members, or changes their role. A viewer loses access. */
    public Mono<Void> changeMembers(Domain domain, String spaceId, Collection<SpaceSnapshot.Member> changed) {
        return liveMailboxForMemberEvent(domain, spaceId)
            .flatMap(spaceMailbox -> members.change(spaceMailbox.teamMailbox(), changed));
    }

    public Mono<Void> removeMembers(Domain domain, String spaceId, Collection<SpaceSnapshot.Member> removed) {
        return liveMailboxForMemberEvent(domain, spaceId)
            .flatMap(spaceMailbox -> members.remove(spaceMailbox.teamMailbox(), removed));
    }

    /** A deleted space: its team mailbox loses its members, and is deleted after the retention. Its mail stays until then. */
    public Mono<Void> close(Optional<Domain> domain, String spaceId) {
        return domain.map(Flux::just).orElseGet(index::domains)
            .concatMap(candidate -> liveMailbox(candidate, spaceId))
            .concatMap(this::close)
            .then();
    }

    /** After a snapshot of an organization: its live spaces the snapshot did not list are gone. */
    public Mono<Void> closeUnlisted(String organizationId, Domain domain, Collection<String> listedSpaceIds) {
        Set<String> listed = ImmutableSet.copyOf(listedSpaceIds);
        return index.list(domain)
            .filter(entry -> !listed.contains(entry.spaceId()))
            .concatMap(entry -> liveMailbox(domain, entry.spaceId()))
            .filter(spaceMailbox -> spaceMailbox.binding().map(SpaceBinding::organizationId).filter(organizationId::equals).isPresent())
            .concatMap(this::close)
            .then();
    }

    private Mono<Void> close(SpaceMailbox spaceMailbox) {
        return members.removeAll(spaceMailbox.teamMailbox())
            .then(spaceMailboxes.markDeleted(spaceMailbox))
            .doOnSuccess(any -> LOGGER.info("Closed the team mailbox {} of space {}", address(spaceMailbox),
                spaceMailbox.binding().map(SpaceBinding::spaceId).orElse("")));
    }

    /** Makes a team mailbox the one of a space, then asks ldap-rest for the space so that its members are set. */
    public Mono<Void> bind(SpaceMailbox spaceMailbox, SpaceRef space) {
        return spaceMailboxes.bind(spaceMailbox, space)
            .then(spaceMailboxes.unmarkDeleted(spaceMailbox))
            .then(index.put(spaceMailbox.teamMailbox().domain(), space.spaceId(), spaceMailbox.teamMailbox()))
            .then(publisher.requestSync(SpacePublisher.SyncScope.space(space)));
    }

    /** Indexes every team mailbox bound to a space, for the spaces bound before the index existed. */
    public Mono<Void> reindex(Runnable onIndexed) {
        return spaceMailboxes.listAll()
            .flatMap(spaceMailbox -> Mono.justOrEmpty(spaceMailbox.binding())
                .flatMap(binding -> index.put(spaceMailbox.teamMailbox().domain(), binding.spaceId(), spaceMailbox.teamMailbox())
                    .then(Mono.fromRunnable(onIndexed))), ReactorUtils.DEFAULT_CONCURRENCY)
            .then();
    }

    public SpaceMailboxes spaceMailboxes() {
        return spaceMailboxes;
    }

    private Mono<SpaceMailbox> liveMailboxForMemberEvent(Domain domain, String spaceId) {
        return liveMailbox(domain, spaceId)
            .switchIfEmpty(Mono.fromRunnable(() -> LOGGER.info("Skipping member event: space {} has no live team mailbox", spaceId)));
    }

    private Mono<SpaceMailbox> liveMailbox(Domain domain, String spaceId) {
        return index.get(domain, spaceId)
            .flatMap(spaceMailboxes::read)
            .filter(spaceMailbox -> spaceMailbox.isLiveFor(spaceId));
    }

    private static String address(SpaceMailbox spaceMailbox) {
        return spaceMailbox.teamMailbox().asMailAddress().asString();
    }
}
