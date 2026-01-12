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

package com.linagora.tmail.vault.blob;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;

public class BlobIdTimeGenerator {
    public static final String BLOB_ID_GENERATING_FORMAT = "%d/%02d/%s";
    public static final Pattern BLOB_ID_TIME_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(.*)$");

    private final BlobId.Factory blobIdFactory;
    private final Clock clock;

    @Inject
    public BlobIdTimeGenerator(BlobId.Factory blobIdFactory, Clock clock) {
        this.blobIdFactory = blobIdFactory;
        this.clock = clock;
    }

    BlobId currentBlobId() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonthValue();
        int year = now.getYear();

        return new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, year, month, blobIdFactory.of(UUID.randomUUID().toString()).asString()));
    }

    Optional<ZonedDateTime> blobIdEndTime(BlobId blobId) {
        return Optional.of(BLOB_ID_TIME_PATTERN.matcher(blobId.asString()))
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

    public BlobId toDeletedMessageBlobId(String blobId) {
        return Optional.of(BLOB_ID_TIME_PATTERN.matcher(blobId))
            .filter(Matcher::matches)
            .map(matcher -> {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                String subBlobId = matcher.group(3);
                return (BlobId) new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, year, month, blobIdFactory.parse(subBlobId).asString()));
            }).orElseGet(() -> blobIdFactory.parse(blobId));
    }
}
