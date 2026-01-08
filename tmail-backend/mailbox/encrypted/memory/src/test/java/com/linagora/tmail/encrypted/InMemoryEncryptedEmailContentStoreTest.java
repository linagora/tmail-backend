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

package com.linagora.tmail.encrypted;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public class InMemoryEncryptedEmailContentStoreTest implements EncryptedEmailContentStoreContract {
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private BlobStore blobStore;
    private InMemoryEncryptedEmailContentStore inMemoryEncryptedEmailContentStore;

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

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();

    @BeforeEach
    void setUp() {
        blobStore = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), MemoryBlobStoreFactory.builder()
            .blobIdFactory(BLOB_ID_FACTORY)
            .passthrough());
        inMemoryEncryptedEmailContentStore = new InMemoryEncryptedEmailContentStore(blobStore);
    }

    @Override
    public EncryptedEmailContentStore testee() {
        return inMemoryEncryptedEmailContentStore;
    }

    @Override
    public MessageId randomMessageId() {
        return InMemoryMessageId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }

    @Override
    public BlobStore blobStore() {
        return blobStore;
    }
}
