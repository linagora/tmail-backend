package com.linagora.tmail.blob.blobid.list;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.blob.aes.CryptoConfig;
import org.junit.jupiter.api.Test;

public class BlobStoreModulesChooserTest {

    @Test
    void provideBlobStoreShouldReturnNoEncryptionWhenNoneConfigured() {
        assertThat(BlobStoreModulesChooser.chooseModules(BlobStoreConfiguration.builder()
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
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .filteredOn(module -> module instanceof BlobStoreModulesChooser.MultiSaveDeclarationModule)
            .hasSize(1);
    }
}
