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

import org.reactivestreams.Publisher;

import com.linagora.tmail.mailbox.quota.model.ExtraQuotaSum;
import com.linagora.tmail.mailbox.quota.model.UserWithSpecificQuota;

public interface UserQuotaReporter {
    Publisher<UserWithSpecificQuota> usersWithSpecificQuota();

    Publisher<Long> usersWithSpecificQuotaCount();

    Publisher<ExtraQuotaSum> usersExtraQuotaSum();
}
