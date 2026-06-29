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

import org.apache.james.core.Username;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Holds the list of users that have already been migrated to the new system. A migrated user is
 * routed towards the new backend, others are routed towards the old one.
 */
public interface MigratedUsersRepository {
    Mono<Void> addMigratedUser(Username username);

    Mono<Void> removeMigratedUser(Username username);

    Mono<Boolean> isMigrated(Username username);

    Flux<Username> listMigratedUsers();
}
