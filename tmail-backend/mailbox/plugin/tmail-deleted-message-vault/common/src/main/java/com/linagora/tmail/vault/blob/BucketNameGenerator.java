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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.vault.blob;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BucketName;

public class BucketNameGenerator {
    private static final Pattern BUCKET_NAME_PATTERN = Pattern.compile("deleted-messages-([0-9]{4})-([0-9]{2})-(01)");
    private static final String BUCKET_NAME_GENERATING_FORMAT = "deleted-messages-%d-%02d-01";

    private final Clock clock;

    @Inject
    public BucketNameGenerator(Clock clock) {
        this.clock = clock;
    }

    BucketName currentBucket() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonthValue();
        int year = now.getYear();
        return BucketName.of(String.format(BUCKET_NAME_GENERATING_FORMAT, year, month));
    }

    Optional<ZonedDateTime> bucketEndTime(BucketName bucketName) {
        return Optional.of(BUCKET_NAME_PATTERN.matcher(bucketName.asString()))
            .filter(Matcher::matches)
            .map(matcher -> {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                return firstDayOfNextMonth(year, month);
            });
    }

    private ZonedDateTime firstDayOfNextMonth(int year, int month) {
        return LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(clock.getZone());
    }
}
