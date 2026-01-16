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

import static com.linagora.tmail.vault.blob.BlobIdTimeGenerator.BLOB_ID_GENERATING_FORMAT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class BlobIdTimeGeneratorTest {
    private static final Instant NOW = Instant.parse("2007-07-03T10:15:30.00Z");
    private static final Clock CLOCK = new UpdatableTickingClock(NOW);

    interface BlobIdTimeGeneratorContract {
        BlobId.Factory blobIdFactory();

        BlobIdTimeGenerator blobIdTimeGenerator();

        @Test
        default void currentBlobIdShouldReturnBlobIdFormattedWithYearAndMonthPrefix() {
            String currentBlobId = blobIdTimeGenerator().currentBlobId().asString();

            int firstSlash = currentBlobId.indexOf('/');
            int secondSlash = currentBlobId.indexOf('/', firstSlash + 1);
            String prefix = currentBlobId.substring(0, secondSlash);

            assertThat(prefix).isEqualTo("2007/07");
        }

        @Test
        default void shouldReturnNextMonthAsEndTime() {
            BlobId blobId = new PlainBlobId(String.format(BLOB_ID_GENERATING_FORMAT, 2018, 7, blobIdFactory().of(UUID.randomUUID().toString()).asString()));

            assertThat(blobIdTimeGenerator().blobIdEndTime(blobId))
                .contains(ZonedDateTime.parse("2018-08-01T00:00:00.000000000Z[UTC]"));
        }

        @Test
        default void shouldReturnProperDeletedMessageBlobIdFromString() {
            BlobId currentBlobId = blobIdTimeGenerator().currentBlobId();
            BlobId deletedMessageBlobId = blobIdTimeGenerator().toDeletedMessageBlobId(currentBlobId.asString());

            assertThat(deletedMessageBlobId).isEqualTo(currentBlobId);
        }

        @Test
        default void shouldFallbackToOldDeletedBlobIdFromString() {
            BlobId currentBlobId = blobIdFactory().of(UUID.randomUUID().toString());
            BlobId deletedMessageBlobId = blobIdTimeGenerator().toDeletedMessageBlobId(currentBlobId.asString());

            assertThat(deletedMessageBlobId).isEqualTo(currentBlobId);
        }
    }

    @Nested
    class PlainBlobIdTimeGeneratorTest implements BlobIdTimeGeneratorContract {
        @Override
        public BlobId.Factory blobIdFactory() {
            return new PlainBlobId.Factory();
        }

        @Override
        public BlobIdTimeGenerator blobIdTimeGenerator() {
            return new BlobIdTimeGenerator(blobIdFactory(), CLOCK);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "2018-07-cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
            "2018-07/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
            "2018/07-cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
            "cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
            "18/07/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
            "2018/7/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
            "07/2018/cddf9c7c-12f1-4ce7-9993-8606b9fb8816",
        })
        public void shouldBeEmptyWhenPassingNonWellFormattedBlobId(String blobIdAsString) {
            BlobId blobId = blobIdFactory().of(blobIdAsString);

            assertThat(blobIdTimeGenerator().blobIdEndTime(blobId)).isEmpty();
        }
    }

    @Nested
    class GenerationAwareBlobIdTimeGeneratorTest implements BlobIdTimeGeneratorContract {
        @Override
        public BlobId.Factory blobIdFactory() {
            return new GenerationAwareBlobId.Factory(CLOCK, new PlainBlobId.Factory(), GenerationAwareBlobId.Configuration.DEFAULT);
        }

        @Override
        public BlobIdTimeGenerator blobIdTimeGenerator() {
            return new BlobIdTimeGenerator(blobIdFactory(), CLOCK);
        }
    }

    @Nested
    class MinIOGenerationAwareBlobIdTimeGeneratorTest implements BlobIdTimeGeneratorContract {
        @Override
        public BlobId.Factory blobIdFactory() {
            return new MinIOGenerationAwareBlobId.Factory(CLOCK, GenerationAwareBlobId.Configuration.DEFAULT, new PlainBlobId.Factory());
        }

        @Override
        public BlobIdTimeGenerator blobIdTimeGenerator() {
            return new BlobIdTimeGenerator(blobIdFactory(), CLOCK);
        }
    }
}
