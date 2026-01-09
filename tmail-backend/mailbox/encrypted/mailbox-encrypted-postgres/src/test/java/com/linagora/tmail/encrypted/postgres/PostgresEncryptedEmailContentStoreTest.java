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

package com.linagora.tmail.encrypted.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.EncryptedEmailContentStore;
import com.linagora.tmail.encrypted.EncryptedEmailContentStoreContract;
import com.linagora.tmail.encrypted.EncryptedEmailContentStoreContract$;
import com.linagora.tmail.encrypted.postgres.table.PostgresEncryptedEmailStoreModule;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEncryptedEmailContentStoreTest implements EncryptedEmailContentStoreContract {
    private static final BlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private final PostgresMessageId.Factory messageIdFactory = new PostgresMessageId.Factory();
    private BlobStore blobStore;
    private PostgresEncryptedEmailBlobReferenceSource blobReferenceSource;
    private PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresEncryptedEmailStoreModule.MODULE);

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();

    static class MetricableBlobStoreExtension implements BeforeEachCallback {
        private RecordingMetricFactory metricFactory;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            this.metricFactory = new RecordingMetricFactory();
        }

        public RecordingMetricFactory getMetricFactory() {
            return metricFactory;
        }
    }

    @BeforeEach
    void setUp() {
        blobStore = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), MemoryBlobStoreFactory.builder()
            .blobIdFactory(BLOB_ID_FACTORY)
            .passthrough());
        encryptedEmailStoreDAO = new PostgresEncryptedEmailStoreDAO(postgresExtension.getDefaultPostgresExecutor(), BLOB_ID_FACTORY);
        blobReferenceSource = new PostgresEncryptedEmailBlobReferenceSource(encryptedEmailStoreDAO);
    }

    @Override
    public EncryptedEmailContentStore testee() {
        return new PostgresEncryptedEmailContentStore(encryptedEmailStoreDAO, blobStore);
    }

    @Override
    public MessageId randomMessageId() {
        return messageIdFactory.generate();
    }

    @Override
    public BlobStore blobStore() {
        return blobStore;
    }

    @Test
    public void blobReferencesShouldBeEmptyByDefault() {
        assertThat(Flux.from(blobReferenceSource.listReferencedBlobs()).collectList().block())
            .isEmpty();
    }

    @Test
    public void blobReferencesShouldReturnAddedValues() {
        Mono.from(testee()
                .store(randomMessageId(), EncryptedEmailContentStoreContract$.MODULE$.ENCRYPTED_EMAIL_CONTENT()))
            .block();

        assertThat(Flux.from(blobReferenceSource.listReferencedBlobs()).collectList().block())
            .hasSize(1);
    }
}
