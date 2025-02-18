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

package com.linagora.tmail.team;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.store.quota.DefaultQuotaChangeNotifier;
import org.apache.james.user.api.UsersRepository;

import reactor.core.publisher.Flux;

public class TMailQuotaUsernameSupplier implements DefaultQuotaChangeNotifier.UsernameSupplier {
    private final UsersRepository usersRepository;
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public TMailQuotaUsernameSupplier(UsersRepository usersRepository, TeamMailboxRepository teamMailboxRepository) {
        this.usersRepository = usersRepository;
        this.teamMailboxRepository = teamMailboxRepository;
    }

    @Override
    public Flux<Username> get() {
        return Flux.merge(usersRepository.listReactive(),
            Flux.from(teamMailboxRepository.listTeamMailboxes()).map(teamMailbox -> Username.of(teamMailbox.asMailAddress().asString())));
    }
}
