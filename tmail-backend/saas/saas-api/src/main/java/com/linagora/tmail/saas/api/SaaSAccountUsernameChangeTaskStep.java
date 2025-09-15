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
 *******************************************************************/

package com.linagora.tmail.saas.api;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSAccountUsernameChangeTaskStep implements UsernameChangeTaskStep {
    private final SaaSAccountRepository saaSAccountRepository;

    @Inject
    public SaaSAccountUsernameChangeTaskStep(SaaSAccountRepository saaSAccountRepository) {
        this.saaSAccountRepository = saaSAccountRepository;
    }

    @Override
    public StepName name() {
        return new StepName("SaaSAccountUsernameChangeTaskStep");
    }

    @Override
    public int priority() {
        return 9;
    }

    @Override
    public Publisher<Void> changeUsername(Username oldUsername, Username newUsername) {
        return Mono.from(saaSAccountRepository.getSaaSAccount(newUsername))
            .flatMap(newSaaSAccount -> {
                if (emptySaaSAccount(newSaaSAccount)) {
                    return migrateSaaSAccount(oldUsername, newUsername);
                }
                return Mono.empty();
            })
            .then(Mono.from(saaSAccountRepository.deleteSaaSAccount(oldUsername)));
    }

    private boolean emptySaaSAccount(SaaSAccount saaSAccount) {
        return saaSAccount.equals(SaaSAccount.DEFAULT);
    }

    private Mono<Void> migrateSaaSAccount(Username oldUsername, Username newUsername) {
        return Mono.from(saaSAccountRepository.getSaaSAccount(oldUsername))
            .flatMap(oldSaaSAccount -> Mono.from(saaSAccountRepository.upsertSaasAccount(newUsername, oldSaaSAccount)));
    }
}
