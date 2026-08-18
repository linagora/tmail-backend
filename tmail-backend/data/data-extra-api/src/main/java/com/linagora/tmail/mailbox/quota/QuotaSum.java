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

package com.linagora.tmail.mailbox.quota;

import org.apache.james.core.quota.QuotaType;
import org.apache.james.core.quota.QuotaCurrentValue;

public record QuotaSum(long count, long size) {
    public static final QuotaSum ZERO = new QuotaSum(0L, 0L);

    public QuotaSum accumulate(QuotaCurrentValue value) {
        if (value.getQuotaType().equals(QuotaType.COUNT)) {
            return new QuotaSum(count + value.getCurrentValue(), size);
        }
        if (value.getQuotaType().equals(QuotaType.SIZE)) {
            return new QuotaSum(count, size + value.getCurrentValue());
        }
        return this;
    }

    public QuotaSum sum(QuotaSum other) {
        return new QuotaSum(count + other.count, size + other.size);
    }
}
