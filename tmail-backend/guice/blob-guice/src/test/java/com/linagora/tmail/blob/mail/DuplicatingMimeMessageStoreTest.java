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

package com.linagora.tmail.blob.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;

import jakarta.mail.internet.MimeMessage;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.util.MimeMessageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DuplicatingMimeMessageStoreTest {
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private static final BucketName MAIL_PROCESSING = MailProcessingConfiguration.MAIL_PROCESSING_BUCKET;

    private MemoryBlobStoreDAO blobStoreDAO;
    private RecordingMetricFactory metricFactory;
    private Store<MimeMessage, MimeMessagePartsId> testee;
    private Store<MimeMessage, MimeMessagePartsId> deduplicatingStore;

    @BeforeEach
    void setUp() {
        blobStoreDAO = new MemoryBlobStoreDAO();
        metricFactory = new RecordingMetricFactory();
        BlobStore deduplicatingBlobStore = BlobStoreFactory.builder()
            .blobStoreDAO(blobStoreDAO)
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .deduplication();

        testee = new DuplicatingMimeMessageStore.Factory(deduplicatingBlobStore, blobStoreDAO, BLOB_ID_FACTORY,
            MailProcessingConfiguration.duplicated(), metricFactory)
            .mimeMessageStore();
        deduplicatingStore = MimeMessageStore.factory(deduplicatingBlobStore).mimeMessageStore();
    }

    private MimeMessage message() throws Exception {
        return MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .setText("Important mail content")
            .build();
    }

    @Test
    void factoryShouldReturnTheDeduplicatingStoreWhenDeduplicationIsEnabled() {
        BlobStore deduplicatingBlobStore = BlobStoreFactory.builder()
            .blobStoreDAO(blobStoreDAO)
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .deduplication();

        assertThat(new DuplicatingMimeMessageStore.Factory(deduplicatingBlobStore, blobStoreDAO, BLOB_ID_FACTORY,
            MailProcessingConfiguration.DEDUPLICATED, metricFactory)
            .mimeMessageStore())
            .isNotInstanceOf(DuplicatingMimeMessageStore.class);
    }

    @Test
    void mailStoreShouldPreserveContent() throws Exception {
        MimeMessage message = message();

        MimeMessagePartsId parts = testee.save(message).block();

        assertThat(MimeMessageUtil.asString(testee.read(parts).block()))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void mailStoreShouldPreserveBigContent() throws Exception {
        MimeMessage message = MimeMessageBuilder.mimeMessageBuilder()
            .addFrom("any@any.com")
            .addToRecipient("toddy@any.com")
            .setSubject("Important Mail")
            .setText(Strings.repeat("Important mail content\r\n", 20000))
            .build();

        MimeMessagePartsId parts = testee.save(message).block();

        assertThat(MimeMessageUtil.asString(testee.read(parts).block()))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void saveShouldWriteASingleBlobInTheMailProcessingBucket() throws Exception {
        MimeMessagePartsId parts = testee.save(message()).block();

        assertThat(Flux.from(blobStoreDAO.listBlobs(MAIL_PROCESSING)).collectList().block())
            .containsExactly(parts.getBodyBlobId());
    }

    @Test
    void saveShouldNotWriteInTheDeduplicatingBucket() throws Exception {
        testee.save(message()).block();

        assertThat(Flux.from(blobStoreDAO.listBlobs(BucketName.DEFAULT)).collectList().block())
            .isEmpty();
    }

    @Test
    void saveShouldFlagTheHeaderBlobId() throws Exception {
        MimeMessagePartsId parts = testee.save(message()).block();

        assertThat(parts.getHeaderBlobId().asString()).isEqualTo(DuplicatingMimeMessageStore.NO_HEADER_BLOB);
    }

    @Test
    void saveShouldNotDeduplicate() throws Exception {
        MimeMessagePartsId parts1 = testee.save(message()).block();
        MimeMessagePartsId parts2 = testee.save(message()).block();

        assertThat(parts1.getBodyBlobId()).isNotEqualTo(parts2.getBodyBlobId());
        assertThat(Flux.from(blobStoreDAO.listBlobs(MAIL_PROCESSING)).collectList().block())
            .hasSize(2);
    }

    @Test
    void deleteShouldRemoveTheUnderlyingBlob() throws Exception {
        MimeMessagePartsId parts = testee.save(message()).block();

        Mono.from(testee.delete(parts)).block();

        assertThat(Flux.from(blobStoreDAO.listBlobs(MAIL_PROCESSING)).collectList().block())
            .isEmpty();
    }

    @Test
    void readShouldThrowUponDeletedMail() throws Exception {
        MimeMessagePartsId parts = testee.save(message()).block();

        Mono.from(testee.delete(parts)).block();

        assertThatThrownBy(() -> testee.read(parts).block())
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void deleteShouldNotDeleteOtherMails() throws Exception {
        MimeMessage message = message();
        MimeMessagePartsId parts1 = testee.save(message()).block();
        MimeMessagePartsId parts2 = testee.save(message).block();

        Mono.from(testee.delete(parts1)).block();

        assertThat(MimeMessageUtil.asString(testee.read(parts2).block()))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void theHeaderMarkerShouldSurviveABlobIdSerializationRoundTrip() throws Exception {
        BlobId.Factory generationAwareBlobIdFactory = new GenerationAwareBlobId.Factory(Clock.systemUTC(), BLOB_ID_FACTORY,
            GenerationAwareBlobId.Configuration.DEFAULT);

        MimeMessagePartsId parts = testee.save(message()).block();

        MimeMessagePartsId deserialized = MimeMessagePartsId.builder()
            .headerBlobId(generationAwareBlobIdFactory.parse(parts.getHeaderBlobId().asString()))
            .bodyBlobId(generationAwareBlobIdFactory.parse(parts.getBodyBlobId().asString()))
            .build();

        assertThat(DuplicatingMimeMessageStore.isFullMessageBlob(deserialized)).isTrue();
    }

    @Test
    void readShouldSupportPartsWrittenByTheDeduplicatingStore() throws Exception {
        MimeMessage message = message();
        MimeMessagePartsId legacyParts = deduplicatingStore.save(message).block();

        assertThat(MimeMessageUtil.asString(testee.read(legacyParts).block()))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void deleteShouldNotRemoveBlobsWrittenByTheDeduplicatingStore() throws Exception {
        MimeMessagePartsId legacyParts = deduplicatingStore.save(message()).block();

        Mono.from(testee.delete(legacyParts)).block();

        assertThat(Flux.from(blobStoreDAO.listBlobs(BucketName.DEFAULT)).collectList().block())
            .containsExactlyInAnyOrder(legacyParts.getHeaderBlobId(), legacyParts.getBodyBlobId());
    }

    @Test
    void deduplicatedMailsShouldRemainReadableAfterTheirDeletionAttempt() throws Exception {
        MimeMessage message = message();
        MimeMessagePartsId legacyParts = deduplicatingStore.save(message).block();

        Mono.from(testee.delete(legacyParts)).block();

        assertThat(MimeMessageUtil.asString(testee.read(legacyParts).block()))
            .isEqualTo(MimeMessageUtil.asString(message));
    }

    @Test
    void messageSizeShouldMatchTheUploadedBytes() throws Exception {
        MimeMessage message = message();

        assertThat(DuplicatingMimeMessageStore.messageSize(message))
            .isEqualTo(streamSize(message));
    }

    @Test
    void messageSizeShouldMatchTheUploadedBytesForWrappedMessages() throws Exception {
        MimeMessage message = new MimeMessageWrapper(message());

        assertThat(DuplicatingMimeMessageStore.messageSize(message))
            .isEqualTo(streamSize(message));
    }

    @Test
    void messageSizeShouldMatchTheUploadedBytesForModifiedMessages() throws Exception {
        MimeMessageWrapper message = new MimeMessageWrapper(message());
        message.addHeader("X-Added-By-A-Mailet", "a value added while the mail was in transit");
        message.saveChanges();

        assertThat(DuplicatingMimeMessageStore.messageSize(message))
            .isEqualTo(streamSize(message));
    }

    @Test
    void saveShouldPublishATimerMetric() throws Exception {
        testee.save(message()).block();

        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.SAVE_TIMER_NAME)).hasSize(1);
    }

    @Test
    void readShouldPublishATimerMetric() throws Exception {
        MimeMessagePartsId parts = testee.save(message()).block();

        testee.read(parts).block();

        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.READ_TIMER_NAME)).hasSize(1);
    }

    @Test
    void deleteShouldPublishATimerMetric() throws Exception {
        MimeMessagePartsId parts = testee.save(message()).block();

        Mono.from(testee.delete(parts)).block();

        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.DELETE_TIMER_NAME)).hasSize(1);
    }

    @Test
    void readShouldPublishADedicatedTimerMetricForDeduplicatedMails() throws Exception {
        MimeMessagePartsId legacyParts = deduplicatingStore.save(message()).block();

        testee.read(legacyParts).block();

        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.DEDUPLICATED_READ_TIMER_NAME)).hasSize(1);
        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.READ_TIMER_NAME)).isEmpty();
    }

    @Test
    void deleteShouldPublishADedicatedTimerMetricForDeduplicatedMails() throws Exception {
        MimeMessagePartsId legacyParts = deduplicatingStore.save(message()).block();

        Mono.from(testee.delete(legacyParts)).block();

        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.DEDUPLICATED_DELETE_TIMER_NAME)).hasSize(1);
        assertThat(metricFactory.executionTimesFor(DuplicatingMimeMessageStore.DELETE_TIMER_NAME)).isEmpty();
    }

    private long streamSize(MimeMessage message) throws Exception {
        try (InputStream in = new MimeMessageInputStream(message)) {
            return in.transferTo(OutputStream.nullOutputStream());
        }
    }
}
