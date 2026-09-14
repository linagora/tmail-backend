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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.james.blob.api.BucketName;
import org.apache.james.modules.mailbox.ConfigurationComponent;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.blob.sharding.TmailBlobStoreShardingConfiguration;

/**
 * Reads the sharding configuration off an actual <code>blob.properties</code> file, through the very
 * {@link PropertiesProvider} the server uses.
 *
 * <p>James reads properties with a comma list delimiter, and hands them over wrapped in a
 * {@code DelegatedPropertiesConfiguration} that splits and strips quotes once more. A plain
 * {@code PropertiesConfiguration} does neither, so a unit test built on one would not tell us anything about
 * {@code tmail.blobstore.shards.ommited.buckets}, the one property here holding a list.</p>
 */
class TmailBlobStoreShardingConfigurationReadingTest {
    @TempDir
    File workingDirectory;

    private TmailBlobStoreShardingConfiguration blobProperties(String... lines) throws Exception {
        File configurationDirectory = new File(workingDirectory, "conf");
        Files.createDirectories(configurationDirectory.toPath());
        Files.writeString(new File(configurationDirectory, ConfigurationComponent.NAME + ".properties").toPath(),
            String.join("\n", lines), StandardCharsets.UTF_8);

        return TmailBlobStoreShardingConfiguration.from(propertiesProvider().getConfigurations(ConfigurationComponent.NAMES));
    }

    private PropertiesProvider propertiesProvider() {
        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .build();
        return new PropertiesProvider(new FileSystemImpl(configuration.directories()), configuration.configurationPath());
    }

    @Test
    void shouldBeDisabledWhenShardCountIsNotSet() throws Exception {
        assertThat(blobProperties("implementation=s3"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.DISABLED);
    }

    @Test
    void shouldReadTheShardCount() throws Exception {
        assertThat(blobProperties("tmail.blobstore.shards=256"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.of(256));
    }

    @Test
    void shouldNotFailWhenOmittedBucketsIsNotSet() throws Exception {
        assertThat(blobProperties("tmail.blobstore.shards=256").omittedBuckets())
            .isEmpty();
    }

    @Test
    void shouldReadASingleOmittedBucket() throws Exception {
        assertThat(blobProperties(
            "tmail.blobstore.shards=256",
            "tmail.blobstore.shards.ommited.buckets=jmap-uploads"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.of(256, BucketName.of("jmap-uploads")));
    }

    @Test
    void shouldReadEveryCommaSeparatedOmittedBucket() throws Exception {
        assertThat(blobProperties(
            "tmail.blobstore.shards=256",
            "tmail.blobstore.shards.ommited.buckets=jmap-uploads,mail-processing"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.of(256,
                BucketName.of("jmap-uploads"), BucketName.of("mail-processing")));
    }

    @Test
    void shouldTrimOmittedBuckets() throws Exception {
        assertThat(blobProperties(
            "tmail.blobstore.shards=256",
            "tmail.blobstore.shards.ommited.buckets= jmap-uploads ,  mail-processing "))
            .isEqualTo(TmailBlobStoreShardingConfiguration.of(256,
                BucketName.of("jmap-uploads"), BucketName.of("mail-processing")));
    }

    @Test
    void shouldIgnoreEmptyOmittedBuckets() throws Exception {
        assertThat(blobProperties(
            "tmail.blobstore.shards=256",
            "tmail.blobstore.shards.ommited.buckets=jmap-uploads,,mail-processing,"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.of(256,
                BucketName.of("jmap-uploads"), BucketName.of("mail-processing")));
    }

    @Test
    void shouldSupportOmittedBucketsSpreadOverSeveralLines() throws Exception {
        assertThat(blobProperties(
            "tmail.blobstore.shards=256",
            "tmail.blobstore.shards.ommited.buckets=jmap-uploads",
            "tmail.blobstore.shards.ommited.buckets=mail-processing"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.of(256,
                BucketName.of("jmap-uploads"), BucketName.of("mail-processing")));
    }

    /**
     * Pins down why the omitted buckets are read as an array: {@code getString} would silently return the first
     * bucket only, leaving the others sharded with no error whatsoever.
     */
    @Test
    void readingOmittedBucketsAsAStringShouldLoseAllButTheFirst() throws Exception {
        File configurationDirectory = new File(workingDirectory, "conf");
        Files.createDirectories(configurationDirectory.toPath());
        Files.writeString(new File(configurationDirectory, ConfigurationComponent.NAME + ".properties").toPath(),
            "tmail.blobstore.shards.ommited.buckets=jmap-uploads,mail-processing", StandardCharsets.UTF_8);

        org.apache.commons.configuration2.Configuration blobProperties = propertiesProvider()
            .getConfigurations(ConfigurationComponent.NAMES);

        assertThat(blobProperties.getString("tmail.blobstore.shards.ommited.buckets"))
            .isEqualTo("jmap-uploads");
        assertThat(blobProperties.getStringArray("tmail.blobstore.shards.ommited.buckets"))
            .containsExactly("jmap-uploads", "mail-processing");
    }

    @Test
    void shouldBeDisabledWhenBlobPropertiesHoldsNothingRelevant() throws Exception {
        assertThat(blobProperties("objectstorage.namespace=blobs", "objectstorage.bucketPrefix=tmail-"))
            .isEqualTo(TmailBlobStoreShardingConfiguration.DISABLED);
    }
}
