package com.linagora.tmail.blob.blobguice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.modules.mailbox.CassandraCacheSessionModule;
import org.junit.jupiter.api.Test;

public class BlobStoreCacheModulesChooserTest {

    @Test
    void chooseModulesShouldReturnCacheDisabledModuleWhenCacheDisabled() {
        assertThat(BlobStoreCacheModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()
            .noSecondaryS3BlobStoreConfig()))
            .hasSize(1)
            .first()
            .isInstanceOf(BlobStoreCacheModulesChooser.CacheDisabledModule.class);
    }

    @Test
    void chooseModulesShouldReturnCacheEnabledAndCassandraCacheModulesWhenCacheEnabled() {
        assertThat(BlobStoreCacheModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .enableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()
            .noSecondaryS3BlobStoreConfig()))
            .hasSize(2)
            .allSatisfy(module ->
                assertThat(module).isOfAnyClassIn(
                    BlobStoreCacheModulesChooser.CacheEnabledModule.class,
                    CassandraCacheSessionModule.class));
    }
}
