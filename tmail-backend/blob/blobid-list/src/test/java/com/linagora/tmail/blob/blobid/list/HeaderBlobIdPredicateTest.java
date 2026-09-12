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

package com.linagora.tmail.blob.blobid.list;

import static org.apache.james.mailbox.cassandra.mail.ContentRecoveryMessageContentSaver.HEADER_BLOB_ID_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdEntropy;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.blob.deduplication.MinIOGenerationAwareBlobId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HeaderBlobIdPredicateTest {
    /**
     * A content addressed id whose payload ends with the four characters of the header suffix: the
     * encoding spells them out like any others, so only the length tells it apart from a header.
     */
    private static BlobId contentAddressedIdEndingWithHeaderSuffix(BlobId.Factory blobIdFactory) {
        int payloadLength = blobIdFactory.encoding().encode(new byte[BlobIdEntropy.entropyBytes()]).length();
        return blobIdFactory.of("A".repeat(payloadLength - HEADER_BLOB_ID_SUFFIX.length()) + HEADER_BLOB_ID_SUFFIX);
    }

    abstract static class Contract {
        abstract BlobId.Factory blobIdFactory();

        private HeaderBlobIdPredicate testee() {
            return new HeaderBlobIdPredicate(blobIdFactory());
        }

        @Test
        void shouldAcceptHeaderBlobs() {
            assertThat(testee().test(blobIdFactory().random().withSuffix(HEADER_BLOB_ID_SUFFIX)))
                .isTrue();
        }

        @Test
        void shouldRejectContentAddressedBlobs() {
            assertThat(testee().test(blobIdFactory().random()))
                .isFalse();
        }

        @Test
        void shouldRejectContentAddressedBlobsEndingWithTheHeaderSuffix() {
            assertThat(testee().test(contentAddressedIdEndingWithHeaderSuffix(blobIdFactory())))
                .isFalse();
        }
    }

    @Nested
    class Plain extends Contract {
        @Override
        BlobId.Factory blobIdFactory() {
            return new PlainBlobId.Factory();
        }
    }

    @Nested
    class GenerationAware extends Contract {
        @Override
        BlobId.Factory blobIdFactory() {
            return new GenerationAwareBlobId.Factory(Clock.systemUTC(), new PlainBlobId.Factory(),
                GenerationAwareBlobId.Configuration.DEFAULT);
        }
    }

    @Nested
    class MinIOGenerationAware extends Contract {
        @Override
        BlobId.Factory blobIdFactory() {
            return new MinIOGenerationAwareBlobId.Factory(Clock.systemUTC(),
                GenerationAwareBlobId.Configuration.DEFAULT, new PlainBlobId.Factory());
        }
    }
}
