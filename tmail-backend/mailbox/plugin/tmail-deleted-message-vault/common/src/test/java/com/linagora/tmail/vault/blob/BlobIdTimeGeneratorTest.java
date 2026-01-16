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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;

import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Test;

class BlobIdTimeGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = new UpdatableTickingClock(NOW);

    @Test
    void currentBlobIdShouldReturnBlobIdFormattedWithYearAndMonthPrefix() {
        String currentBlobId = BlobIdTimeGenerator.currentBlobId(CLOCK).asString();

        assertThat(currentBlobId).matches("2007/07/././.*");
    }
}
