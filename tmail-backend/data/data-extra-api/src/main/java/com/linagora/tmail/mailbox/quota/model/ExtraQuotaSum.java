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

package com.linagora.tmail.mailbox.quota.model;

import java.util.Optional;

import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;

public record ExtraQuotaSum(
    QuotaSizeLimit storageLimit,
    QuotaCountLimit countLimit) {
    public static ExtraQuotaSum NONE = new ExtraQuotaSum(QuotaSizeLimit.size(0L), QuotaCountLimit.count(0L));

    public static ExtraQuotaSum merge(ExtraQuotaSum sum, ExtraQuotaSum anotherSum) {
        QuotaSizeLimit mergedSize = mergeSizeLimits(sum.storageLimit(), anotherSum.storageLimit());
        QuotaCountLimit mergedCount = mergeCountLimits(sum.countLimit(), anotherSum.countLimit());
        return new ExtraQuotaSum(mergedSize, mergedCount);
    }

    private static QuotaSizeLimit mergeSizeLimits(QuotaSizeLimit storageLimit, QuotaSizeLimit anotherStorageLimit) {
        if (storageLimit.equals(QuotaSizeLimit.unlimited()) || anotherStorageLimit.equals(QuotaSizeLimit.unlimited())) {
            return QuotaSizeLimit.unlimited();
        }
        return QuotaSizeLimit.size(storageLimit.asLong() + anotherStorageLimit.asLong());
    }

    private static QuotaCountLimit mergeCountLimits(QuotaCountLimit countLimit, QuotaCountLimit anotherCountLimit) {
        if (countLimit.equals(QuotaCountLimit.unlimited()) || anotherCountLimit.equals(QuotaCountLimit.unlimited())) {
            return QuotaCountLimit.unlimited();
        }
        return QuotaCountLimit.count(countLimit.asLong() + anotherCountLimit.asLong());
    }

    public static ExtraQuotaSum calculateExtraQuota(Limits commonQuota, Limits userQuota) {
        QuotaSizeLimit extraSize = calculateExtraSize(commonQuota.sizeLimit(), userQuota.sizeLimit());
        QuotaCountLimit extraCount = calculateExtraCount(commonQuota.countLimit(), userQuota.countLimit());
        return new ExtraQuotaSum(extraSize, extraCount);
    }

    private static QuotaSizeLimit calculateExtraSize(Optional<QuotaSizeLimit> commonLimit, Optional<QuotaSizeLimit> userLimit) {
        if (userLimit.isEmpty() || commonLimit.isEmpty()) {
            return QuotaSizeLimit.size(0L);
        }

        QuotaSizeLimit userQuota = userLimit.get();
        QuotaSizeLimit commonQuota = commonLimit.get();
        if (commonQuota.equals(QuotaSizeLimit.unlimited())) {
            return QuotaSizeLimit.size(0L);
        }
        if (userQuota.equals(QuotaSizeLimit.unlimited())) {
            return QuotaSizeLimit.unlimited();
        }
        return QuotaSizeLimit.size(Math.max(0L, userQuota.asLong() - commonQuota.asLong()));
    }

    private static QuotaCountLimit calculateExtraCount(Optional<QuotaCountLimit> commonLimit, Optional<QuotaCountLimit> userLimit) {
        if (userLimit.isEmpty() || commonLimit.isEmpty()) {
            return QuotaCountLimit.count(0L);
        }

        QuotaCountLimit userQuota = userLimit.get();
        QuotaCountLimit commonQuota = commonLimit.get();
        if (commonQuota.equals(QuotaCountLimit.unlimited())) {
            return QuotaCountLimit.count(0L);
        }
        if (userQuota.equals(QuotaCountLimit.unlimited())) {
            return QuotaCountLimit.unlimited();
        }
        return QuotaCountLimit.count(Math.max(0L, userQuota.asLong() - commonQuota.asLong()));
    }
}
