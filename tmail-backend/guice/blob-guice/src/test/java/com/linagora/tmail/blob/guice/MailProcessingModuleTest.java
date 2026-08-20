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

package com.linagora.tmail.blob.guice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.blob.mail.DuplicatingMimeMessageStore;
import com.linagora.tmail.blob.mail.MailProcessingConfiguration;

class MailProcessingModuleTest {
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();

    private static Injector injector(MailProcessingConfiguration configuration) {
        BlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        BlobStore blobStore = BlobStoreFactory.builder()
            .blobStoreDAO(blobStoreDAO)
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .deduplication();

        Module dependencies = binder -> {
            binder.bind(BlobStore.class).toInstance(blobStore);
            binder.bind(BlobStoreDAO.class).toInstance(blobStoreDAO);
            binder.bind(BlobId.Factory.class).toInstance(BLOB_ID_FACTORY);
            binder.bind(MailProcessingConfiguration.class).toInstance(configuration);
            binder.bind(MetricFactory.class).toInstance(new RecordingMetricFactory());
        };

        return Guice.createInjector(Modules.override(new MailProcessingModule()).with(dependencies));
    }

    @Test
    void shouldSubstituteJamesMimeMessageStoreFactory() {
        assertThat(injector(MailProcessingConfiguration.DEDUPLICATED).getInstance(MimeMessageStore.Factory.class))
            .isInstanceOf(DuplicatingMimeMessageStore.Factory.class);
    }

    @Test
    void shouldDeduplicateByDefault() {
        MimeMessageStore.Factory factory = injector(MailProcessingConfiguration.DEDUPLICATED)
            .getInstance(MimeMessageStore.Factory.class);

        assertThat(factory.mimeMessageStore()).isNotInstanceOf(DuplicatingMimeMessageStore.class);
    }

    @Test
    void shouldDuplicateWhenDeduplicationIsDisabled() {
        MimeMessageStore.Factory factory = injector(MailProcessingConfiguration.duplicated())
            .getInstance(MimeMessageStore.Factory.class);

        assertThat(factory.mimeMessageStore()).isInstanceOf(DuplicatingMimeMessageStore.class);
    }
}
