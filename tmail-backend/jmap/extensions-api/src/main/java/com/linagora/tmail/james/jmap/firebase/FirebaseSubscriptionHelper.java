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

package com.linagora.tmail.james.jmap.firebase;

import static com.linagora.tmail.james.jmap.model.FirebaseSubscription.EXPIRES_TIME_MAX_DAY;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Optional;

import com.linagora.tmail.james.jmap.model.FirebaseSubscription;
import com.linagora.tmail.james.jmap.model.FirebaseSubscriptionExpiredTime;

import scala.Option;

public class FirebaseSubscriptionHelper {
    public static boolean isInThePast(Option<FirebaseSubscriptionExpiredTime> expire, Clock clock) {
        return expire.map(value -> isInThePast(value, clock)).getOrElse(() -> false);
    }

    public static boolean isInThePast(FirebaseSubscriptionExpiredTime expire, Clock clock) {
        return expire.isBefore(ZonedDateTime.now(clock));
    }

    public static FirebaseSubscriptionExpiredTime evaluateExpiresTime(Optional<ZonedDateTime> inputTime, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime maxExpiresTime = now.plusDays(EXPIRES_TIME_MAX_DAY());
        return FirebaseSubscriptionExpiredTime.apply(inputTime.filter(input -> input.isBefore(maxExpiresTime))
            .orElse(maxExpiresTime));
    }

    public static boolean isNotOutdatedSubscription(FirebaseSubscription subscription, Clock clock) {
        return subscription.expires().isAfter(ZonedDateTime.now(clock));
    }
}
