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

package com.linagora.tmail.saas.rabbitmq.subscription;

import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.quota.QuotaSizeLimit;

public class SaaSSubscriptionUtils {
    public static Result combine(Result result1, Result result2) {
        if (result1.getStatus().ordinal() >= result2.getStatus().ordinal()) {
            return result1;
        }
        return result2;
    }

    public static QuotaSizeLimit asQuotaSizeLimit(Long storageQuota) {
        if (storageQuota == -1) {
            return QuotaSizeLimit.unlimited();
        }
        return QuotaSizeLimit.size(storageQuota);
    }
}
