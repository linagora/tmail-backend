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

package com.linagora.tmail.james.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.james.SearchConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.PostgresTmailConfiguration.EventBusImpl;

class PostgresTmailConfigurationTest {
    private static final String LUCENE_REJECTION_MESSAGE = "Lucene search is not supported by the postgres app with the RabbitMQ event bus";

    @TempDir
    File workingDirectory;

    private PostgresTmailConfiguration.Builder builder() {
        return PostgresTmailConfiguration.builder()
            .workingDirectory(workingDirectory)
            .blobStore(BlobStoreConfiguration.builder()
                .postgres()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .enableSingleSave());
    }

    @Test
    void luceneShouldBeAcceptedWithInMemoryEventBus() {
        PostgresTmailConfiguration configuration = builder()
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.lucene())
            .eventBusImpl(EventBusImpl.IN_MEMORY)
            .build();

        assertThat(configuration.searchConfiguration().getImplementation())
            .isEqualTo(SearchConfiguration.Implementation.Lucene);
    }

    @Test
    void luceneShouldBeRejectedWithRabbitMQEventBus() {
        assertThatThrownBy(() -> builder()
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.lucene())
            .eventBusImpl(EventBusImpl.RABBITMQ)
            .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(LUCENE_REJECTION_MESSAGE);
    }

    @Test
    void luceneShouldBeRejectedWithRabbitMQAndRedisEventBus() {
        assertThatThrownBy(() -> builder()
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.lucene())
            .eventBusImpl(EventBusImpl.RABBITMQ_AND_REDIS)
            .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(LUCENE_REJECTION_MESSAGE);
    }

    @Test
    void luceneShouldBeRejectedWhenRabbitMQIsConfigured() throws IOException {
        Path configurationDirectory = Files.createDirectories(workingDirectory.toPath().resolve("conf"));
        Files.writeString(configurationDirectory.resolve("rabbitmq.properties"), "uri=amqp://localhost:5672\n");

        assertThatThrownBy(() -> builder()
            .searchConfiguration(SearchConfiguration.lucene())
            .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(LUCENE_REJECTION_MESSAGE);
    }

    @Test
    void openSearchShouldBeAcceptedWithRabbitMQEventBus() {
        PostgresTmailConfiguration configuration = builder()
            .configurationFromClasspath()
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusImpl(EventBusImpl.RABBITMQ)
            .build();

        assertThat(configuration.eventBusImpl()).isEqualTo(EventBusImpl.RABBITMQ);
    }
}
