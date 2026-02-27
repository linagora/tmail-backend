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

package com.linagora.tmail.saas.filter;

import jakarta.inject.Inject;

import org.apache.james.core.Username;

import com.linagora.tmail.james.jmap.event.ApplyWhenFilter;
import com.linagora.tmail.saas.api.SaaSAccountRepository;

import reactor.core.publisher.Mono;

public class SaaSNonPayingUser implements ApplyWhenFilter {
    private final SaaSAccountRepository saaSAccountRepository;

    @Inject
    public SaaSNonPayingUser(SaaSAccountRepository saaSAccountRepository) {
        this.saaSAccountRepository = saaSAccountRepository;
    }

    @Override
    public Mono<Boolean> isEligible(Username username) {
        return Mono.from(saaSAccountRepository.getSaaSAccount(username))
            .map(saasAccount -> !saasAccount.isPaying());
    }
}
