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

package com.linagora.tmail.integration.probe;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.Inject;

import reactor.core.publisher.Mono;

public class QuotaUsageProbe implements GuiceProbe {
    private final CurrentQuotaManager currentQuotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;

    @Inject
    public QuotaUsageProbe(CurrentQuotaManager currentQuotaManager, UserQuotaRootResolver userQuotaRootResolver) {
        this.currentQuotaManager = currentQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
    }

    public void setCurrentQuotas(Username user, long count, long size) {
        QuotaRoot quotaRoot = userQuotaRootResolver.forUser(user);
        Mono.from(currentQuotaManager.setCurrentQuotas(
            new QuotaOperation(quotaRoot, QuotaCountUsage.count(count), QuotaSizeUsage.size(size)))).block();
    }
}
