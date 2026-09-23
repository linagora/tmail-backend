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

import java.net.URI;

import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3RequestOption;
import org.apache.james.blob.objectstorage.aws.Region;
import org.apache.james.blob.objectstorage.aws.sse.S3SSECConfiguration;
import org.apache.james.blob.zstd.CompressionConfiguration;
import org.junit.jupiter.api.Test;

public class BlobStoreModulesChooserTest {
    private static final URI S3_ENDPOINT = URI.create("http://127.0.0.1:9000");
    private static final Region REGION = Region.of("us-east-1");

    @Test
    void provideBlobStoreShouldReturnNoEncryptionWhenNoneConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.NoEncryptionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnEncryptionWhenConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .passthrough()
            .withCryptoConfig(CryptoConfig.builder()
                .password("myPass".toCharArray())
                // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
                .salt("73616c7479")
                .build())
            .compressionConfig(CompressionConfiguration.disabled())
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.EncryptionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnNoCompressionWhenNoneConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.NoCompressionModule)
            .hasSize(1);
    }

    @Test
    void provideBlobStoreShouldReturnCompressionWhenConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .passthrough()
            .withNoCryptoConfig()
            .compressionConfig(CompressionConfiguration.builder()
                .enabled(true)
                .threshold(1)
                .build())
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.CompressionModule)
            .hasSize(1);
    }

    @Test
    void objectStorageShouldReturnSingleSaveDeclarationModuleWhenEnableSingleSave() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .enableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.SingleSaveDeclarationModule)
            .hasSize(1);
    }

    @Test
    void objectStorageShouldReturnMultiSaveDeclarationModuleWhenDisableSingleSave() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.MultiSaveDeclarationModule)
            .hasSize(1);
    }

    @Test
    void provideS3RequestOptionShouldTakeIntoAccountIfNoneMatchConfigurationWhenSSECDisabled() throws Exception {
        BlobStoreModulesChooser.ObjectStorageBlobStoreDAODeclarationModule module = new BlobStoreModulesChooser.ObjectStorageBlobStoreDAODeclarationModule();

        assertThat(module.provideS3RequestOption(s3BlobStoreConfiguration(false, false)).ifNoneMatch()).isFalse();
        assertThat(module.provideS3RequestOption(s3BlobStoreConfiguration(false, true)).ifNoneMatch()).isTrue();
    }

    @Test
    void provideS3RequestOptionShouldTakeIntoAccountIfNoneMatchConfigurationWhenSSECEnabled() throws Exception {
        BlobStoreModulesChooser.ObjectStorageBlobStoreDAODeclarationModule module = new BlobStoreModulesChooser.ObjectStorageBlobStoreDAODeclarationModule();

        S3RequestOption s3RequestOption = module.provideS3RequestOption(s3BlobStoreConfiguration(true, true));

        assertThat(s3RequestOption.ifNoneMatch()).isTrue();
        assertThat(s3RequestOption.ssec().enable()).isTrue();
    }

    private S3BlobStoreConfiguration s3BlobStoreConfiguration(boolean ssecEnabled, boolean ifNoneMatchEnabled) {
        S3BlobStoreConfiguration.Builder.ReadyToBuild builder = S3BlobStoreConfiguration.builder()
            .authConfiguration(AwsS3AuthConfiguration.builder()
                .endpoint(S3_ENDPOINT)
                .accessKeyId("accessKeyId")
                .secretKey("secretKey")
                .build())
            .region(REGION)
            .ifNoneMatchEnabled(ifNoneMatchEnabled);
        if (ssecEnabled) {
            return builder.ssecEnabled()
                .ssecConfiguration(new S3SSECConfiguration.Basic(S3SSECConfiguration.ENCRYPTION_S3_SSEC_ALGORITHM_DEFAULT, "myPass", "salt"))
                .build();
        }
        return builder.build();
    }
}
