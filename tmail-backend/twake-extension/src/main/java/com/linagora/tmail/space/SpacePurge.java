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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

/**
 * Deletes the team mailboxes of spaces closed for longer than the retention, which frees their address. Nothing runs
 * it on its own: an external scheduler, like a Kubernetes CronJob, calls {@code POST /spaces?action=purge}.
 */
public class SpacePurge {
    public static class Context {
        public record Snapshot(long deletedTeamMailboxes, List<String> failedTeamMailboxes) {
        }

        private final AtomicLong deletedTeamMailboxes = new AtomicLong();
        private final ConcurrentLinkedQueue<String> failedTeamMailboxes = new ConcurrentLinkedQueue<>();

        public Snapshot snapshot() {
            return new Snapshot(deletedTeamMailboxes.get(), ImmutableList.copyOf(failedTeamMailboxes));
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(SpacePurge.class);

    private final SpaceMailboxes spaceMailboxes;
    private final SpaceIndex index;
    private final TeamMailboxRepository teamMailboxRepository;
    private final SpaceConfiguration configuration;
    private final Clock clock;

    @Inject
    public SpacePurge(SpaceMailboxes spaceMailboxes, SpaceIndex index, TeamMailboxRepository teamMailboxRepository,
                      SpaceConfiguration configuration, Clock clock) {
        this.spaceMailboxes = spaceMailboxes;
        this.index = index;
        this.teamMailboxRepository = teamMailboxRepository;
        this.configuration = configuration;
        this.clock = clock;
    }

    public Mono<Task.Result> purge(Context context) {
        Instant threshold = clock.instant().minus(configuration.retention());
        return index.domains()
            .concatMap(domain -> index.list(domain)
                .concatMap(entry -> Mono.justOrEmpty(OptionConverters.toJava(TeamMailbox.fromJava(domain, entry.teamMailboxName())))
                    .flatMap(spaceMailboxes::read)
                    .filter(spaceMailbox -> closedBefore(spaceMailbox, entry.spaceId(), threshold))
                    .flatMap(spaceMailbox -> delete(domain, entry.spaceId(), spaceMailbox, context))
                    .onErrorResume(e -> {
                        String address = entry.teamMailboxName() + "@" + domain.asString();
                        LOGGER.error("Failed to read the team mailbox {} of space {}", address, entry.spaceId(), e);
                        context.failedTeamMailboxes.add(address);
                        return Mono.just(Task.Result.PARTIAL);
                    })))
            .reduce(Task.Result.COMPLETED, Task::combine)
            .defaultIfEmpty(Task.Result.COMPLETED);
    }

    private boolean closedBefore(SpaceMailbox spaceMailbox, String spaceId, Instant threshold) {
        return spaceMailbox.binding()
            .filter(binding -> binding.spaceId().equals(spaceId))
            .flatMap(SpaceBinding::deletedAt)
            .filter(deletedAt -> deletedAt.isBefore(threshold))
            .isPresent();
    }

    private Mono<Task.Result> delete(Domain domain, String spaceId, SpaceMailbox spaceMailbox, Context context) {
        String address = spaceMailbox.teamMailbox().asMailAddress().asString();
        return Mono.from(teamMailboxRepository.deleteTeamMailbox(spaceMailbox.teamMailbox()))
            .then(index.remove(domain, spaceId))
            .then(Mono.fromCallable(() -> {
                LOGGER.info("Deleted the team mailbox {}, its space was closed over {} ago", address, configuration.retention());
                context.deletedTeamMailboxes.incrementAndGet();
                return Task.Result.COMPLETED;
            }))
            .onErrorResume(e -> {
                LOGGER.error("Failed to delete the team mailbox {} of a closed space", address, e);
                context.failedTeamMailboxes.add(address);
                return Mono.just(Task.Result.PARTIAL);
            });
    }
}
