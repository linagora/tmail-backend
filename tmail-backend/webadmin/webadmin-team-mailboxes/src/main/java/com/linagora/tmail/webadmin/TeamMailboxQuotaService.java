package com.linagora.tmail.webadmin;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.webadmin.dto.ValidatedQuotaDTO;

import com.linagora.tmail.team.TeamMailbox;

public class TeamMailboxQuotaService {
    private final MaxQuotaManager maxQuotaManager;

    @Inject
    public TeamMailboxQuotaService(MaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;
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
}
