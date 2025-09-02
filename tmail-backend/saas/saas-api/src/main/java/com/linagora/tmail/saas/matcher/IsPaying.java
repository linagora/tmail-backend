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

package com.linagora.tmail.saas.matcher;

import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

/**
 * Matches mail where the sender has a SaaS plan.
 */
public class IsPaying extends GenericMatcher {
    private final SaaSAccountRepository saaSAccountRepository;

    @Inject
    public IsPaying(SaaSAccountRepository saaSAccountRepository) {
        this.saaSAccountRepository = saaSAccountRepository;
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        return Mono.justOrEmpty(mail.getMaybeSender().asOptional())
            .map(Username::fromMailAddress)
            .filterWhen(sender -> Mono.from(saaSAccountRepository.getSaaSAccount(sender)).map(SaaSAccount::isPaying))
            .map(any -> mail.getRecipients())
            .switchIfEmpty(Mono.just(ImmutableList.of()))
            .block();
    }
}
