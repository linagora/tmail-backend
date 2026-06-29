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

import jakarta.inject.Inject;

import org.apache.james.core.Username;

import com.linagora.tmail.migration.core.MigrationProxyConfiguration.Target;

import reactor.core.publisher.Mono;

/**
 * Picks the {@link Backend} a given user should be routed to: the new backend when the user has been
 * migrated, the old backend otherwise.
 */
public class BackendResolver {
    private final MigratedUsersRepository migratedUsersRepository;
    private final MigrationProxyConfiguration configuration;

    @Inject
    public BackendResolver(MigratedUsersRepository migratedUsersRepository, MigrationProxyConfiguration configuration) {
        this.migratedUsersRepository = migratedUsersRepository;
        this.configuration = configuration;
    }

    public Mono<Backend> resolve(Username username) {
        return migratedUsersRepository.isMigrated(username)
            .map(migrated -> migrated ? Target.NEW : Target.OLD)
            .map(configuration::backend);
    }
}
