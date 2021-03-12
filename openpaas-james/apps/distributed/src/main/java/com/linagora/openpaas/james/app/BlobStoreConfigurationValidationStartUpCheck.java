package com.linagora.openpaas.james.app;

import javax.inject.Inject;

import org.apache.james.lifecycle.api.StartUpCheck;

import com.google.common.annotations.VisibleForTesting;

public class BlobStoreConfigurationValidationStartUpCheck implements StartUpCheck {
    private static final String BLOB_STORE_CONFIGURATION_VALIDATION = "blobStore-configuration-validation";
    private final BlobStoreConfiguration blobStoreConfiguration;
    private final EventsourcingStorageStrategy eventsourcingStorageStrategy;

    @VisibleForTesting
    @Inject
    BlobStoreConfigurationValidationStartUpCheck(BlobStoreConfiguration blobStoreConfiguration, EventsourcingStorageStrategy eventsourcingStorageStrategy) {
        this.blobStoreConfiguration = blobStoreConfiguration;
        this.eventsourcingStorageStrategy = eventsourcingStorageStrategy;
    }

    @Override
    public CheckResult check() {
        try {
            eventsourcingStorageStrategy.registerStorageStrategy(blobStoreConfiguration.storageStrategy());
            return CheckResult.builder()
                .checkName(BLOB_STORE_CONFIGURATION_VALIDATION)
                .resultType(ResultType.GOOD)
                .build();
        } catch (IllegalStateException e) {
            return CheckResult.builder()
                .checkName(BLOB_STORE_CONFIGURATION_VALIDATION)
                .resultType(ResultType.BAD)
                .description(e.getMessage())
                .build();
        }
    }

    @Override
    public String checkName() {
        return BLOB_STORE_CONFIGURATION_VALIDATION;
    }
}
