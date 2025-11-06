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

package com.linagora.tmail.integration.probe;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.utils.GuiceProbe;

import com.google.inject.Inject;

public class MaxQuotaManagerProbe implements GuiceProbe {
    private final MaxQuotaManager maxQuotaManager;
    private final UserQuotaRootResolver userQuotaRootResolver;

    @Inject
    public MaxQuotaManagerProbe(MaxQuotaManager maxQuotaManager, UserQuotaRootResolver userQuotaRootResolver) {
        this.maxQuotaManager = maxQuotaManager;
        this.userQuotaRootResolver = userQuotaRootResolver;
    }

    public void setDomainMaxStorage(Domain domain, QuotaSizeLimit size) throws MailboxException {
        maxQuotaManager.setDomainMaxStorage(domain, size);
    }

    public void setMaxStorage(Username username, QuotaSizeLimit maxStorageQuota) throws MailboxException {
        maxQuotaManager.setMaxStorage(userQuotaRootResolver.forUser(username), maxStorageQuota);
    }

}
