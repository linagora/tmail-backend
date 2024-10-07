package com.linagora.tmail.blob.blobguice;

import java.util.List;
import java.util.Optional;

import org.apache.james.blob.aes.AESBlobStoreDAO;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.blobstore.BlobDeduplicationGCModule;
import org.apache.james.modules.blobstore.validation.EventsourcingStorageStrategy;
import org.apache.james.modules.blobstore.validation.StorageStrategyModule;
import org.apache.james.modules.mailbox.BlobStoreAPIModule;
import org.apache.james.modules.objectstorage.S3BlobStoreModule;
import org.apache.james.modules.objectstorage.S3BucketModule;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.linagora.tmail.blob.blobid.list.BlobIdList;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAO;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreModule;

public class BlobStoreModulesChooser {
    private static final String FIRST_LEVEL = "first_level_blob_store_dao";
    private static final String SECOND_LEVEL = "second_level_blob_store_dao";
    private static final String THIRD_LEVEL = "third_level_blob_store_dao";
    private static final String FOURTH_LEVEL = "fourth_level_blob_store_dao";
    private static final String SECONDARY_BLOB_STORE_DAO = "secondary_blob_store_dao";
    private static final String SECONDARY_S3_CLIENT_FACTORY = "secondary_s3_client_factory";

    static class BaseObjectStorageModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new S3BucketModule());
            install(new S3BlobStoreModule());
            bind(BlobStoreDAO.class).annotatedWith(Names.named(FIRST_LEVEL))
                .to(S3BlobStoreDAO.class);
        }

        @Provides
        @Singleton
        BlobStoreDAO provdePrimaryBlobStoreDAO(@Named(FOURTH_LEVEL) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class NoSecondaryObjectStorageModule extends AbstractModule {
        @Provides
        @Singleton
        @Named(SECOND_LEVEL)
        BlobStoreDAO provdeBlobStoreDAO(@Named(FIRST_LEVEL) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class SecondaryObjectStorageModule extends AbstractModule {
        private S3BlobStoreConfiguration secondaryS3BlobStoreConfiguration;

        public SecondaryObjectStorageModule(S3BlobStoreConfiguration secondaryS3BlobStoreConfiguration) {
            this.secondaryS3BlobStoreConfiguration = secondaryS3BlobStoreConfiguration;
        }

        @Provides
        @Singleton
        @Named(SECOND_LEVEL)
        BlobStoreDAO provdeBlobStoreDAO(@Named(FIRST_LEVEL) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class EncryptionModule extends AbstractModule {
        private final CryptoConfig cryptoConfig;

        EncryptionModule(CryptoConfig cryptoConfig) {
            this.cryptoConfig = cryptoConfig;
        }

        @Provides
        CryptoConfig cryptoConfig() {
            return cryptoConfig;
        }

        @Provides
        @Singleton
        @Named(THIRD_LEVEL)
        BlobStoreDAO providePrelastBlobStoreDAO(@Named(SECOND_LEVEL) BlobStoreDAO blobStoreDAO) {
            return new AESBlobStoreDAO(blobStoreDAO, cryptoConfig);
        }

    }

    static class NoEncryptionModule extends AbstractModule {
        @Provides
        @Singleton
        @Named(THIRD_LEVEL)
        BlobStoreDAO providePrelastBlobStoreDAO(@Named(SECOND_LEVEL) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class SingleSaveDeclarationModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new SingleSaveBlobStoreModule());
        }

        @Provides
        @Singleton
        SingleSaveBlobStoreDAO singleSaveBlobStoreDAO(@Named(THIRD_LEVEL) BlobStoreDAO blobStoreDAO,
                                                      BlobIdList blobIdList,
                                                      BucketName defaultBucketName) {
            return new SingleSaveBlobStoreDAO(blobStoreDAO, blobIdList, defaultBucketName);
        }

        @Provides
        @Singleton
        @Named(FOURTH_LEVEL)
        public BlobStoreDAO provideLast(SingleSaveBlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class MultiSaveDeclarationModule extends AbstractModule {

        @Provides
        @Singleton
        @Named(FOURTH_LEVEL)
        public BlobStoreDAO provideLastBlobStoreDAO(@Named(THIRD_LEVEL) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    public static Module chooseSecondaryObjectStorageModule(Optional<S3BlobStoreConfiguration> maybeS3BlobStoreConfiguration) {
        if (maybeS3BlobStoreConfiguration.isPresent()) {
            return new SecondaryObjectStorageModule(maybeS3BlobStoreConfiguration.get());
        } else {
            return new NoSecondaryObjectStorageModule();
        }
    }

    public static Module chooseEncryptionModule(Optional<CryptoConfig> cryptoConfig) {
        if (cryptoConfig.isPresent()) {
            return new EncryptionModule(cryptoConfig.get());
        }
        return new NoEncryptionModule();
    }

    public static Module chooseSaveDeclarationModule(boolean singleSaveEnabled) {
        if (singleSaveEnabled) {
            return new SingleSaveDeclarationModule();
        }
        return new MultiSaveDeclarationModule();
    }

    private static ImmutableList<Module> chooseStoragePolicyModule(StorageStrategy storageStrategy) {
        return switch (storageStrategy) {
            case DEDUPLICATION -> ImmutableList.of(new BlobDeduplicationGCModule(),
                binder -> binder.bind(BlobStore.class)
                    .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                    .to(DeDuplicationBlobStore.class));
            case PASSTHROUGH -> ImmutableList.of(
                binder -> binder.bind(BlobStore.class)
                    .annotatedWith(Names.named(CachedBlobStore.BACKEND))
                    .to(PassThroughBlobStore.class),
                new BlobStoreAPIModule());
        };
    }

    static class StoragePolicyConfigurationSanityEnforcementModule extends AbstractModule {
        private final BlobStoreConfiguration choosingConfiguration;

        StoragePolicyConfigurationSanityEnforcementModule(BlobStoreConfiguration choosingConfiguration) {
            this.choosingConfiguration = choosingConfiguration;
        }

        @Override
        protected void configure() {
            Multibinder<EventDTOModule<? extends Event, ? extends EventDTO>> eventDTOModuleBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {
            });
            eventDTOModuleBinder.addBinding().toInstance(StorageStrategyModule.STORAGE_STRATEGY);

            bind(BlobStoreConfiguration.class).toInstance(choosingConfiguration);
            bind(EventsourcingStorageStrategy.class).in(Scopes.SINGLETON);

            Multibinder.newSetBinder(binder(), StartUpCheck.class)
                .addBinding()
                .to(BlobStoreConfigurationValidationStartUpCheck.class);
        }
    }

    @VisibleForTesting
    public static List<Module> chooseModules(BlobStoreConfiguration blobStoreConfiguration) {
        return ImmutableList.<Module>builder()
            .add(new BaseObjectStorageModule())
            .add(chooseSecondaryObjectStorageModule(blobStoreConfiguration.maybeSecondaryS3BlobStoreConfiguration()))
            .add(chooseEncryptionModule(blobStoreConfiguration.cryptoConfig()))
            .add(chooseSaveDeclarationModule(blobStoreConfiguration.singleSaveEnabled()))
            .addAll(chooseStoragePolicyModule(blobStoreConfiguration.storageStrategy()))
            .add(new StoragePolicyConfigurationSanityEnforcementModule(blobStoreConfiguration))
            .build();
    }
}