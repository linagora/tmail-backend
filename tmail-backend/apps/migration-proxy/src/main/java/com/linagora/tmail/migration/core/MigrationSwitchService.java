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

package com.linagora.tmail.migration.core;

import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.DisconnectorNotifier.MultipleUserRequest;
import org.apache.james.core.Username;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

/**
 * Marks a user migrated (routed to the new backend) and best-effort disconnects its live proxied
 * sessions. Shared by the webadmin {@code PUT /migratedUsers/{username}} route and the
 * {@code migration:switch} AMQP consumer, the two ways of triggering the same old→new switch.
 */
public class MigrationSwitchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationSwitchService.class);

    private final MigratedUsersRepository migratedUsersRepository;
    private final DisconnectorNotifier disconnectorNotifier;

    @Inject
    public MigrationSwitchService(MigratedUsersRepository migratedUsersRepository, DisconnectorNotifier disconnectorNotifier) {
        this.migratedUsersRepository = migratedUsersRepository;
        this.disconnectorNotifier = disconnectorNotifier;
    }

    public Mono<Void> markMigrated(Username username) {
        return migratedUsersRepository.addMigratedUser(username)
            .then(Mono.fromRunnable(() -> disconnect(username)))
            .then();
    }

    private void disconnect(Username username) {
        // Force the user's live proxied sessions to reconnect so they land on the new backend straight
        // away rather than staying pinned to the old one until they disconnect. The request goes through
        // the event bus so that, in a cluster of migration proxies, the node actually holding the
        // connection closes it, wherever the migration was triggered.
        // Best-effort: the user is already flagged migrated, so a disconnection publishing failure (e.g.
        // a transient event-bus issue) must not fail the caller - the stale sessions will simply
        // reconnect on the new backend the next time they cycle.
        try {
            disconnectorNotifier.disconnect(MultipleUserRequest.of(Set.of(username)));
        } catch (Exception e) {
            LOGGER.warn("Failed to publish the disconnection request for migrated user {}", username.asString(), e);
        }
    }
}
