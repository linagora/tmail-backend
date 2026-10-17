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
import java.time.Duration;
import java.time.Instant;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.space.SpaceAnnotations.SpaceBinding;
import com.linagora.tmail.space.SpaceMailboxes.SpaceMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Deletes the team mailboxes of spaces closed for longer than the retention, which frees their address. Every
 * replica runs it: deleting a team mailbox twice is harmless.
 */
public class SpacePurge implements Startable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpacePurge.class);

    private final SpaceMailboxes spaceMailboxes;
    private final TeamMailboxRepository teamMailboxRepository;
    private final SpaceConfiguration configuration;
    private final Clock clock;
    private Disposable schedule;

    @Inject
    public SpacePurge(SpaceMailboxes spaceMailboxes, TeamMailboxRepository teamMailboxRepository, SpaceConfiguration configuration, Clock clock) {
        this.spaceMailboxes = spaceMailboxes;
        this.teamMailboxRepository = teamMailboxRepository;
        this.configuration = configuration;
        this.clock = clock;
    }

    public void start() {
        schedule = Flux.interval(Duration.ZERO, configuration.purgeInterval(), Schedulers.parallel())
            .onBackpressureDrop()
            .concatMap(tick -> purge()
                .onErrorResume(e -> {
                    LOGGER.error("Failed to purge the team mailboxes of closed spaces", e);
                    return Mono.empty();
                }))
            .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (schedule != null) {
            schedule.dispose();
        }
    }

    public Mono<Void> purge() {
        Instant threshold = clock.instant().minus(configuration.retention());
        return spaceMailboxes.listAll()
            .filter(spaceMailbox -> spaceMailbox.binding()
                .flatMap(SpaceBinding::deletedAt)
                .filter(deletedAt -> deletedAt.isBefore(threshold))
                .isPresent())
            .concatMap(this::delete)
            .then();
    }

    private Mono<Void> delete(SpaceMailbox spaceMailbox) {
        String address = spaceMailbox.teamMailbox().asMailAddress().asString();
        return Mono.from(teamMailboxRepository.deleteTeamMailbox(spaceMailbox.teamMailbox()))
            .doOnSuccess(any -> LOGGER.info("Deleted the team mailbox {}, its space was closed over {} ago", address, configuration.retention()))
            .onErrorResume(e -> {
                LOGGER.error("Failed to delete the team mailbox {} of a closed space, next purge retries", address, e);
                return Mono.empty();
            });
    }
}
