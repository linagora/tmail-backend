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

package com.linagora.tmail.james.jmap.firebase;

import java.util.function.Predicate;

import jakarta.inject.Inject;

import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;

import reactor.core.publisher.Flux;

public class FirebaseSubscriptionDisconnector implements Disconnector {

    private final UsersRepository usersRepository;
    private final FirebaseSubscriptionRepository repository;

    @Inject
    public FirebaseSubscriptionDisconnector(UsersRepository usersRepository, FirebaseSubscriptionRepository repository) {
        this.usersRepository = usersRepository;
        this.repository = repository;
    }

    @Override
    public void disconnect(Predicate<Username> username) {
        Flux.from(usersRepository.listReactive())
            .filter(username)
            .flatMap(repository::revoke)
            .then()
            .block();
    }
}
