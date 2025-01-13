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

import org.apache.james.blob.aes.CryptoConfig;
import org.junit.jupiter.api.Test;

public class BlobStoreModulesChooserTest {

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
            .cryptoConfig(CryptoConfig.builder()
                .password("myPass".toCharArray())
                // Hex.encode("salty".getBytes(StandardCharsets.UTF_8))
                .salt("73616c7479")
                .build())
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.EncryptionModule)
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
}
