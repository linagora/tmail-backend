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

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

import com.google.common.collect.Sets;
import com.linagora.tmail.team.TeamMailbox;

public class TeamMailboxQuotaService {
    private final MaxQuotaManager maxQuotaManager;
    private final QuotaManager quotaManager;

    @Inject
    public TeamMailboxQuotaService(MaxQuotaManager maxQuotaManager, QuotaManager quotaManager) {
        this.maxQuotaManager = maxQuotaManager;
        this.quotaManager = quotaManager;
    }

    public Optional<QuotaCountLimit> getMaxCountQuota(TeamMailbox teamMailbox) throws MailboxException {
        return maxQuotaManager.getMaxMessage(teamMailbox.quotaRoot());
    }

    public void defineMaxCountQuota(TeamMailbox teamMailbox, QuotaCountLimit quotaCount) throws MailboxException {
        maxQuotaManager.setMaxMessage(teamMailbox.quotaRoot(), quotaCount);
    }

    public void deleteMaxCountQuota(TeamMailbox teamMailbox) throws MailboxException {
        maxQuotaManager.removeMaxMessage(teamMailbox.quotaRoot());
    }

    public Optional<QuotaSizeLimit> getMaxSizeQuota(TeamMailbox teamMailbox) throws MailboxException {
        return maxQuotaManager.getMaxStorage(teamMailbox.quotaRoot());
    }

    public void defineMaxSizeQuota(TeamMailbox teamMailbox, QuotaSizeLimit quotaSize) throws MailboxException {
        maxQuotaManager.setMaxStorage(teamMailbox.quotaRoot(), quotaSize);
    }

    public void deleteMaxSizeQuota(TeamMailbox teamMailbox) throws MailboxException {
        maxQuotaManager.removeMaxStorage(teamMailbox.quotaRoot());
    }

    public void defineQuota(TeamMailbox teamMailbox, ValidatedQuotaDTO quota) {
        try {
            QuotaRoot quotaRoot = teamMailbox.quotaRoot();
            if (quota.getCount().isPresent()) {
                maxQuotaManager.setMaxMessage(quotaRoot, quota.getCount().get());
            } else {
                maxQuotaManager.removeMaxMessage(quotaRoot);
            }

            if (quota.getSize().isPresent()) {
                maxQuotaManager.setMaxStorage(quotaRoot, quota.getSize().get());
            } else {
                maxQuotaManager.removeMaxStorage(quotaRoot);
            }
        } catch (MailboxException e) {
            throw new RuntimeException(e);
        }
    }

    public TeamMailboxQuotaDetailsDTO getQuota(TeamMailbox teamMailbox) throws MailboxException {
        QuotaRoot quotaRoot = teamMailbox.quotaRoot();
        QuotaManager.Quotas quotas = quotaManager.getQuotas(quotaRoot);
        TeamMailboxQuotaDetailsDTO.Builder quotaDetails = TeamMailboxQuotaDetailsDTO.builder()
            .occupation(quotas.getStorageQuota(),
                quotas.getMessageQuota());

        mergeMaps(
            maxQuotaManager.listMaxMessagesDetails(quotaRoot),
            maxQuotaManager.listMaxStorageDetails(quotaRoot))
            .forEach(quotaDetails::valueForScope);

        quotaDetails.computed(computedQuota(quotaRoot));
        return quotaDetails.build();
    }

    private Map<Quota.Scope, ValidatedQuotaDTO> mergeMaps(Map<Quota.Scope, QuotaCountLimit> counts, Map<Quota.Scope, QuotaSizeLimit> sizes) {
        return Sets.union(counts.keySet(), sizes.keySet())
            .stream()
            .collect(Collectors.toMap(Function.identity(),
                scope -> ValidatedQuotaDTO
                    .builder()
                    .count(Optional.ofNullable(counts.get(scope)))
                    .size(Optional.ofNullable(sizes.get(scope)))
                    .build()));
    }

    private ValidatedQuotaDTO computedQuota(QuotaRoot quotaRoot) throws MailboxException {
        return ValidatedQuotaDTO
            .builder()
            .count(maxQuotaManager.getMaxMessage(quotaRoot))
            .size(maxQuotaManager.getMaxStorage(quotaRoot))
            .build();
    }
}
