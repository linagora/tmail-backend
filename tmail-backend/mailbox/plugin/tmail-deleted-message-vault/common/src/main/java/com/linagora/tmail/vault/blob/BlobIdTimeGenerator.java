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
import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;

public class BlobIdTimeGenerator {
    public static final String BLOB_ID_GENERATING_FORMAT = "%d/%02d/%s/%s/%s";

    static BlobId currentBlobId(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        int month = now.getMonthValue();
        int year = now.getYear();
        String randomizerA = RandomStringUtils.insecure().nextAlphanumeric(1);
        String randomizerB = RandomStringUtils.insecure().nextAlphanumeric(1);

        return new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, year, month, randomizerA, randomizerB, UUID.randomUUID()));
    }
}
