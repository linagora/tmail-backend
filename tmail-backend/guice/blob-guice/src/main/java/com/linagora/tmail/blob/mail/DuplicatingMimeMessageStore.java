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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.output.CountingOutputStream;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BlobStoreDAO.ByteSourceBlob;
import org.apache.james.blob.api.BlobStoreDAO.InputStreamBlob;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.api.Store;
import org.apache.james.blob.mail.MimeMessagePartsId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.server.core.MimeMessageSource;
import org.apache.james.server.core.MimeMessageWrapper;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.FileBackedOutputStream;

import reactor.core.publisher.Mono;

/**
 * A {@link Store} storing each mail in transit as a standalone blob, thus never deduplicating it with the
 * copies eventually stored within the mailboxes.
 *
 * <p>Mails are written as a single, complete RFC822 object in a dedicated bucket, through the
 * {@link BlobStoreDAO} - bypassing the deduplicating {@link BlobStore} whose <code>delete</code> is a no-op.
 * Blobs are thus effectively deleted upon dequeue / mail repository removal instead of being left over to
 * the deduplication garbage collector. Note that the {@link BlobStoreDAO} is to be the encryption and
 * compression aware one, not the raw one.</p>
 *
 * <p>Given {@link MimeMessagePartsId} carries two blob ids, and given no schema change is desired, the header
 * blob id is used as a discriminator: it is set to {@link #NO_HEADER_BLOB} while the body blob id holds the
 * id of the full message. Parts ids written while deduplication was enabled keep pointing to regular,
 * deduplicated header and body blobs: they are transparently read from - and, upon deletion, left over to
 * the deduplication garbage collector - by the deduplicating store.</p>
 */
public class DuplicatingMimeMessageStore implements Store<MimeMessage, MimeMessagePartsId> {
    /**
     * Marker used as a header blob id, denoting a mail stored as a single, non deduplicated blob.
     *
     * Note: this value needs to survive the serialization round trips of the mail queue and of the mail
     * repositories, hence the comparison upon {@link BlobId#asString()} rather than upon {@link BlobId}
     * equality: reading it back yields whichever {@link BlobId} implementation the deployment relies on.
     */
    public static final String NO_HEADER_BLOB = "EMPTY";
    private static final BlobId HEADER_MARKER = new PlainBlobId(NO_HEADER_BLOB);

    static final String METRIC_PREFIX = "mailProcessing:";
    static final String SAVE_TIMER_NAME = METRIC_PREFIX + "save";
    static final String READ_TIMER_NAME = METRIC_PREFIX + "read";
    static final String DELETE_TIMER_NAME = METRIC_PREFIX + "delete";
    /** Mails written back when deduplication was enabled: their drain is what an operator watches after a switch. */
    static final String DEDUPLICATED_READ_TIMER_NAME = METRIC_PREFIX + "deduplicatedRead";
    static final String DEDUPLICATED_DELETE_TIMER_NAME = METRIC_PREFIX + "deduplicatedDelete";

    public static boolean isFullMessageBlob(MimeMessagePartsId partsId) {
        return NO_HEADER_BLOB.equals(partsId.getHeaderBlobId().asString());
    }

    /**
     * Substitutes James' {@link MimeMessageStore.Factory} - which the mail queue and the mail repositories
     * inject - so that mails in transit are, or are not, deduplicated depending on the configuration.
     */
    public static class Factory extends MimeMessageStore.Factory {
        private final BlobStore blobStore;
        private final BlobStoreDAO blobStoreDAO;
        private final BlobId.Factory blobIdFactory;
        private final MailProcessingConfiguration configuration;
        private final MetricFactory metricFactory;

        @Inject
        public Factory(BlobStore blobStore, BlobStoreDAO blobStoreDAO, BlobId.Factory blobIdFactory,
                       MailProcessingConfiguration configuration, MetricFactory metricFactory) {
            super(blobStore);
            this.blobStore = blobStore;
            this.blobStoreDAO = blobStoreDAO;
            this.blobIdFactory = blobIdFactory;
            this.configuration = configuration;
            this.metricFactory = metricFactory;
        }

        @Override
        public Store<MimeMessage, MimeMessagePartsId> mimeMessageStore() {
            return mimeMessageStore(blobStore.getDefaultBucketName());
        }

        @Override
        public Store<MimeMessage, MimeMessagePartsId> mimeMessageStore(BucketName bucketName) {
            Store<MimeMessage, MimeMessagePartsId> deduplicatingStore = super.mimeMessageStore(bucketName);

            if (configuration.deduplicationEnabled()) {
                return deduplicatingStore;
            }
            return new DuplicatingMimeMessageStore(blobStoreDAO, blobIdFactory,
                configuration.mailProcessingBucket(), deduplicatingStore, metricFactory);
        }
    }

    private final BlobStoreDAO blobStoreDAO;
    private final BlobId.Factory blobIdFactory;
    private final BucketName bucketName;
    private final Store<MimeMessage, MimeMessagePartsId> deduplicatingStore;
    private final MetricFactory metricFactory;

    public DuplicatingMimeMessageStore(BlobStoreDAO blobStoreDAO, BlobId.Factory blobIdFactory, BucketName bucketName,
                                       Store<MimeMessage, MimeMessagePartsId> deduplicatingStore,
                                       MetricFactory metricFactory) {
        this.blobStoreDAO = blobStoreDAO;
        this.blobIdFactory = blobIdFactory;
        this.bucketName = bucketName;
        this.deduplicatingStore = deduplicatingStore;
        this.metricFactory = metricFactory;
    }

    @Override
    public Mono<MimeMessagePartsId> save(MimeMessage message) {
        Preconditions.checkNotNull(message);

        BlobId blobId = blobIdFactory.of(UUID.randomUUID().toString());

        return Mono.from(metricFactory.decoratePublisherWithTimerMetric(SAVE_TIMER_NAME,
            Mono.from(blobStoreDAO.save(bucketName, blobId, ByteSourceBlob.of(asByteSource(message))))
                .thenReturn(MimeMessagePartsId.builder()
                    .headerBlobId(HEADER_MARKER)
                    .bodyBlobId(blobId)
                    .build())));
    }

    @Override
    public Mono<MimeMessage> read(MimeMessagePartsId blobIds) {
        Preconditions.checkNotNull(blobIds);

        if (!isFullMessageBlob(blobIds)) {
            return Mono.from(metricFactory.decoratePublisherWithTimerMetric(DEDUPLICATED_READ_TIMER_NAME,
                deduplicatingStore.read(blobIds)));
        }

        return Mono.from(metricFactory.decoratePublisherWithTimerMetric(READ_TIMER_NAME,
            Mono.from(blobStoreDAO.readReactive(bucketName, blobIds.getBodyBlobId()))
                .flatMap(blob -> Mono.fromCallable(() -> materialize(blob))
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER))
                .map(content -> (MimeMessage) new MimeMessageWrapper(new FullMessageSource(content)))));
    }

    @Override
    public Publisher<Void> delete(MimeMessagePartsId blobIds) {
        Preconditions.checkNotNull(blobIds);

        if (!isFullMessageBlob(blobIds)) {
            return metricFactory.decoratePublisherWithTimerMetric(DEDUPLICATED_DELETE_TIMER_NAME,
                deduplicatingStore.delete(blobIds));
        }

        return metricFactory.decoratePublisherWithTimerMetric(DELETE_TIMER_NAME,
            blobStoreDAO.delete(bucketName, blobIds.getBodyBlobId()));
    }

    private CloseableByteSource materialize(InputStreamBlob blob) throws IOException {
        FileBackedOutputStream out = new FileBackedOutputStream(FILE_THRESHOLD);
        try (InputStream in = blob.payload()) {
            long size = in.transferTo(out);
            out.flush();
            return new FileBackedByteSource(out, size);
        } catch (IOException | RuntimeException e) {
            out.reset();
            out.close();
            throw e;
        }
    }

    private static ByteSource asByteSource(MimeMessage message) {
        return new ByteSource() {
            private final AtomicLong size = new AtomicLong(-1);

            @Override
            public InputStream openStream() throws IOException {
                return fullMessageStream(message);
            }

            @Override
            public long size() throws IOException {
                long knownSize = size.get();
                if (knownSize >= 0) {
                    return knownSize;
                }
                long computedSize = messageSize(message);
                size.set(computedSize);
                return computedSize;
            }
        };
    }

    private static InputStream fullMessageStream(MimeMessage message) throws IOException {
        try {
            return new MimeMessageInputStream(message);
        } catch (MessagingException e) {
            throw new IOException("Failed to generate message stream", e);
        }
    }

    /**
     * Size of the full message, headers included.
     *
     * <p>Beware: this size is relied upon as the content length of the underlying object storage upload, thus
     * needs to be exact. {@link MimeMessage#getSize()} - which only accounts for the body, and which the
     * JavaMail specification documents as an estimate - can not be used here. {@link MimeMessageWrapper}
     * exposes an exact message size, otherwise we count the very bytes we are about to upload.</p>
     */
    @VisibleForTesting
    static long messageSize(MimeMessage message) throws IOException {
        if (message instanceof MimeMessageWrapper) {
            try {
                long size = ((MimeMessageWrapper) message).getMessageSize();
                if (size >= 0) {
                    return size;
                }
            } catch (MessagingException e) {
                throw new IOException("Failed accessing message size", e);
            }
        }

        CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
        try (InputStream in = fullMessageStream(message)) {
            in.transferTo(countingOutputStream);
        }
        return countingOutputStream.getCount();
    }

    private static class FileBackedByteSource extends CloseableByteSource {
        private final FileBackedOutputStream out;
        private final long size;

        private FileBackedByteSource(FileBackedOutputStream out, long size) {
            this.out = out;
            this.size = size;
        }

        @Override
        public InputStream openStream() throws IOException {
            return out.asByteSource().openStream();
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public void close() throws IOException {
            out.reset();
            out.close();
        }
    }

    private static class FullMessageSource implements MimeMessageSource, Disposable {
        private final CloseableByteSource content;
        private final String sourceId;

        private FullMessageSource(CloseableByteSource content) {
            this.content = content;
            this.sourceId = UUID.randomUUID().toString();
        }

        @Override
        public String getSourceId() {
            return sourceId;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return content.openStream();
        }

        @Override
        public long getMessageSize() throws IOException {
            return content.size();
        }

        @Override
        public void dispose() {
            try {
                content.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
