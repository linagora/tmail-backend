package com.linagora.tmail.blob.guice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.modules.mailbox.CassandraCacheSessionModule;
import org.junit.jupiter.api.Test;

public class BlobStoreCacheModulesChooserTest {

    @Test
    void chooseModulesShouldReturnCacheDisabledModuleWhenCacheDisabled() {
        assertThat(BlobStoreCacheModulesChooser.chooseModules(BlobStoreConfiguration.builder()
<<<<<<< HEAD
            .s3()
            .noSecondaryS3BlobStore()
=======
            .implementation(BlobStoreConfiguration.BlobStoreImplName.S3)
>>>>>>> ISSUE-922 Modularize BlobStoreModulesChooser for postgres-app
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .hasSize(1)
            .first()
            .isInstanceOf(BlobStoreCacheModulesChooser.CacheDisabledModule.class);
    }

    @Test
    void chooseModulesShouldReturnCacheEnabledAndCassandraCacheModulesWhenCacheEnabled() {
        assertThat(BlobStoreCacheModulesChooser.chooseModules(BlobStoreConfiguration.builder()
<<<<<<< HEAD
            .s3()
            .noSecondaryS3BlobStore()
=======
            .implementation(BlobStoreConfiguration.BlobStoreImplName.S3)
>>>>>>> ISSUE-922 Modularize BlobStoreModulesChooser for postgres-app
            .enableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .hasSize(2)
            .allSatisfy(module ->
                assertThat(module).isOfAnyClassIn(
                    BlobStoreCacheModulesChooser.CacheEnabledModule.class,
                    CassandraCacheSessionModule.class));
    }
}
