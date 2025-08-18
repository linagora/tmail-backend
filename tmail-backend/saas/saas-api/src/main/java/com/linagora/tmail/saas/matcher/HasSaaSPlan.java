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
import java.util.Locale;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

/**
 * Matches mail where the sender has one of the SaaS plans specified in the match condition.
 * The condition is a comma-separated list of SaaS plan names (e.g., "standard,premium").
 */
public class HasSaaSPlan extends GenericMatcher {
    private final SaaSAccountRepository saaSAccountRepository;
    private Collection<SaaSPlan> saasPlansCondition;

    @Inject
    public HasSaaSPlan(SaaSAccountRepository saaSAccountRepository) {
        this.saaSAccountRepository = saaSAccountRepository;
    }

    @Override
    public void init() throws MessagingException {
        if (Strings.isNullOrEmpty(getCondition())) {
            throw new MessagingException("HasSaaSPlan should have a condition composed of a list of SaaS plans");
        }

        saasPlansCondition = Splitter.on(",")
            .omitEmptyStrings()
            .trimResults()
            .splitToStream(getCondition())
            .map(value -> new SaaSPlan(value.toLowerCase(Locale.US)))
            .collect(ImmutableList.toImmutableList());

        if (saasPlansCondition.isEmpty()) {
            throw new MessagingException("HasSaaSPlan should have at least one SaaS plan passed as a condition");
        }
    }

    @Override
    public final Collection<MailAddress> match(Mail mail) {
        return Mono.justOrEmpty(mail.getMaybeSender().asOptional())
            .map(Username::fromMailAddress)
            .flatMap(sender -> Mono.from(saaSAccountRepository.getSaaSAccount(sender))
                .filter(saaSAccount -> saasPlansCondition.contains(saaSAccount.saaSPlan()))
                .map(any -> mail.getRecipients()))
            .switchIfEmpty(Mono.just(ImmutableList.of()))
            .block();
    }
}
