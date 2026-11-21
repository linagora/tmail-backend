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

package com.linagora.tmail.webadmin;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;

import com.linagora.tmail.webadmin.data.TaskBelongsToDomainPredicate;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesTask;
import com.linagora.tmail.webadmin.vault.TeamMailboxVaultRestoreTask;

public class TeamMailboxTasksBelongsToDomainPredicates {
    public static final TaskBelongsToDomainPredicate VAULT_RESTORE = (domain, details) -> details.getAdditionalInformation()
        .filter(TeamMailboxVaultRestoreTask.AdditionalInformation.class::isInstance)
        .map(TeamMailboxVaultRestoreTask.AdditionalInformation.class::cast)
        .map(info -> {
            try {
                return new MailAddress(info.getTeamMailboxAddress()).getDomain().equals(domain);
            } catch (AddressException e) {
                return false;
            }
        })
        .orElse(false);

    public static final TaskBelongsToDomainPredicate RECOMPUTE_QUOTA = (domain, details) -> details.getAdditionalInformation()
        .filter(RecomputeQuotaTeamMailboxesTask.Details.class::isInstance)
        .map(RecomputeQuotaTeamMailboxesTask.Details.class::cast)
        .map(info -> domain.equals(info.getDomain()))
        .orElse(false);

    private TeamMailboxTasksBelongsToDomainPredicates() {
    }
}
