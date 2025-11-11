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
    QuotaSizeLimit totalExtraStorageLimit,
    QuotaCountLimit totalExtraCountLimit,
    UnlimitedStorageCount totalUnlimitedStorage,
    UnlimitedMessagesCount totalUnlimitedCount) {
    public static final ExtraQuotaSum NONE =
        new ExtraQuotaSum(QuotaSizeLimit.size(0L), QuotaCountLimit.count(0L),
            UnlimitedStorageCount.ZERO, UnlimitedMessagesCount.ZERO);

    public record UnlimitedStorageCount(long value) {
        public static final UnlimitedStorageCount ZERO = new UnlimitedStorageCount(0L);
    }

    public record UnlimitedMessagesCount(long value) {
        public static final UnlimitedMessagesCount ZERO = new UnlimitedMessagesCount(0L);
    }

    private record ExtraQuota<T>(T extraLimit, long unlimitedCount) {}

    public static ExtraQuotaSum merge(ExtraQuotaSum sum, ExtraQuotaSum anotherSum) {
        QuotaSizeLimit mergedSize = mergeSizeLimits(sum.totalExtraStorageLimit(), anotherSum.totalExtraStorageLimit());
        QuotaCountLimit mergedCount = mergeCountLimits(sum.totalExtraCountLimit(), anotherSum.totalExtraCountLimit());

        UnlimitedStorageCount mergedUnlimitedStorage =
            new UnlimitedStorageCount(sum.totalUnlimitedStorage().value() + anotherSum.totalUnlimitedStorage().value());
        UnlimitedMessagesCount mergedUnlimitedCount =
            new UnlimitedMessagesCount(sum.totalUnlimitedCount().value() + anotherSum.totalUnlimitedCount().value());

        return new ExtraQuotaSum(mergedSize, mergedCount, mergedUnlimitedStorage, mergedUnlimitedCount);
    }

    private static QuotaSizeLimit mergeSizeLimits(QuotaSizeLimit limit1, QuotaSizeLimit limit2) {
        return QuotaSizeLimit.size(limit1.asLong() + limit2.asLong());
    }

    private static QuotaCountLimit mergeCountLimits(QuotaCountLimit limit1, QuotaCountLimit limit2) {
        return QuotaCountLimit.count(limit1.asLong() + limit2.asLong());
    }

    public static ExtraQuotaSum calculateExtraQuota(Limits commonQuota, Limits userQuota) {
        Optional<QuotaSizeLimit> commonSize = commonQuota.sizeLimit();
        Optional<QuotaSizeLimit> userSize = userQuota.sizeLimit();
        Optional<QuotaCountLimit> commonCount = commonQuota.countLimit();
        Optional<QuotaCountLimit> userCount = userQuota.countLimit();

        ExtraQuota<QuotaSizeLimit> extraSize = calculateExtraSize(commonSize, userSize);
        ExtraQuota<QuotaCountLimit> extraCount = calculateExtraCount(commonCount, userCount);

        return new ExtraQuotaSum(
            extraSize.extraLimit(),
            extraCount.extraLimit(),
            new UnlimitedStorageCount(extraSize.unlimitedCount()),
            new UnlimitedMessagesCount(extraCount.unlimitedCount()));
    }

    private static ExtraQuota<QuotaSizeLimit> calculateExtraSize(Optional<QuotaSizeLimit> commonLimitOptional, Optional<QuotaSizeLimit> userLimitOptional) {
        if (userLimitOptional.isEmpty() || commonLimitOptional.isEmpty()) {
            return new ExtraQuota<>(QuotaSizeLimit.size(0L), 0);
        }

        QuotaSizeLimit userLimit = userLimitOptional.get();
        QuotaSizeLimit commonLimit = commonLimitOptional.get();

        if (userLimit.equals(QuotaSizeLimit.unlimited())) {
            return new ExtraQuota<>(QuotaSizeLimit.size(0L), 1);
        }

        if (commonLimit.equals(QuotaSizeLimit.unlimited())) {
            return new ExtraQuota<>(QuotaSizeLimit.size(0L), 0);
        }

        long diff = Math.max(0L, userLimit.asLong() - commonLimit.asLong());
        return new ExtraQuota<>(QuotaSizeLimit.size(diff), 0);
    }

    private static ExtraQuota<QuotaCountLimit> calculateExtraCount(Optional<QuotaCountLimit> commonLimitOptional, Optional<QuotaCountLimit> userLimitOptional) {
        if (userLimitOptional.isEmpty() || commonLimitOptional.isEmpty()) {
            return new ExtraQuota<>(QuotaCountLimit.count(0L), 0);
        }

        QuotaCountLimit userLimit = userLimitOptional.get();
        QuotaCountLimit commonLimit = commonLimitOptional.get();

        if (userLimit.equals(QuotaCountLimit.unlimited())) {
            return new ExtraQuota<>(QuotaCountLimit.count(0L), 1);
        }

        if (commonLimit.equals(QuotaCountLimit.unlimited())) {
            return new ExtraQuota<>(QuotaCountLimit.count(0L), 0);
        }

        long diff = Math.max(0L, userLimit.asLong() - commonLimit.asLong());
        return new ExtraQuota<>(QuotaCountLimit.count(diff), 0);
    }
}
