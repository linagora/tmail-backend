package com.linagora.tmail.blob.guice;

import java.util.List;
import java.util.Optional;

import org.apache.james.blob.aes.AESBlobStoreDAO;
import org.apache.james.blob.aes.CryptoConfig;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.cassandra.cache.CachedBlobStore;
import org.apache.james.blob.objectstorage.aws.JamesS3MetricPublisher;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;
import org.apache.james.blob.objectstorage.aws.S3ClientFactory;
import org.apache.james.events.EventBus;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.dto.EventDTOModule;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
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
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.linagora.tmail.blob.blobid.list.BlobIdList;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAO;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreModule;
import com.linagora.tmail.blob.secondaryblobstore.FailedBlobOperationListener;
import com.linagora.tmail.blob.secondaryblobstore.SecondaryBlobStoreDAO;
import com.linagora.tmail.common.event.TmailInjectNameConstants;
import com.linagora.tmail.common.event.TmailReactiveGroupEventListener;

public class BlobStoreModulesChooser {
    public static final String INITIAL_BLOBSTORE_DAO = "initial_blobstore_dao";
    public static final String MAYBE_SECONDARY_BLOBSTORE = "maybe_secondary_blob_store_dao";
    public static final String MAYBE_ENCRYPTION_BLOBSTORE = "maybe_encryption_blob_store_dao";
    public static final String MAYBE_SINGLE_SAVE_BLOBSTORE = "maybe_single_save_blob_store_dao";
    public static final String SECOND_BLOB_STORE_DAO = "second_blob_store_dao";

    static class BaseObjectStorageModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new S3BucketModule());
            install(new S3BlobStoreModule());
            bind(BlobStoreDAO.class).annotatedWith(Names.named(INITIAL_BLOBSTORE_DAO))
                .to(S3BlobStoreDAO.class);
        }

        @Provides
        @Singleton
        BlobStoreDAO provideFinalBlobStoreDAO(@Named(MAYBE_SINGLE_SAVE_BLOBSTORE) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class NoSecondaryObjectStorageModule extends AbstractModule {
        @Provides
        @Singleton
        @Named(MAYBE_SECONDARY_BLOBSTORE)
        BlobStoreDAO provideNoSecondaryBlobStoreDAO(@Named(INITIAL_BLOBSTORE_DAO) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    static class SecondaryObjectStorageModule extends AbstractModule {
        private final SecondaryS3BlobStoreConfiguration secondaryS3BlobStoreConfiguration;

        public SecondaryObjectStorageModule(SecondaryS3BlobStoreConfiguration secondaryS3BlobStoreConfiguration) {
            this.secondaryS3BlobStoreConfiguration = secondaryS3BlobStoreConfiguration;
        }

        @Provides
        @Singleton
        @Named(SECOND_BLOB_STORE_DAO)
        BlobStoreDAO getSecondaryS3BlobStoreDAO(BlobId.Factory blobIdFactory,
                                                MetricFactory metricFactory,
                                                GaugeRegistry gaugeRegistry) {
            S3ClientFactory s3SecondaryClientFactory = new S3ClientFactory(secondaryS3BlobStoreConfiguration.s3BlobStoreConfiguration(),
                () -> new JamesS3MetricPublisher(metricFactory, gaugeRegistry, "secondary_s3"));
            return new S3BlobStoreDAO(s3SecondaryClientFactory, secondaryS3BlobStoreConfiguration.s3BlobStoreConfiguration(), blobIdFactory);
        }

        @ProvidesIntoSet
        TmailReactiveGroupEventListener provideFailedBlobOperationListener(@Named(INITIAL_BLOBSTORE_DAO) BlobStoreDAO firstBlobStoreDAO,
                                                                           @Named(SECOND_BLOB_STORE_DAO) BlobStoreDAO secondBlobStoreDAO) {
            return new FailedBlobOperationListener(firstBlobStoreDAO, secondBlobStoreDAO, secondaryS3BlobStoreConfiguration.secondaryBucketSuffix());
        }

        @Provides
        @Singleton
        @Named(MAYBE_SECONDARY_BLOBSTORE)
        BlobStoreDAO provideSecondaryBlobStoreDAO(@Named(INITIAL_BLOBSTORE_DAO) BlobStoreDAO firstBlobStoreDAO,
                                                  @Named(SECOND_BLOB_STORE_DAO) BlobStoreDAO secondBlobStoreDAO,
                                                  @Named(TmailInjectNameConstants.TMAIL_EVENT_BUS_INJECT_NAME) EventBus eventBus) {
            return new SecondaryBlobStoreDAO(firstBlobStoreDAO, secondBlobStoreDAO, secondaryS3BlobStoreConfiguration.secondaryBucketSuffix(), eventBus);
        }
    }

    static class EncryptionModule extends AbstractModule {
        private final CryptoConfig cryptoConfig;

        public EncryptionModule(CryptoConfig cryptoConfig) {
            this.cryptoConfig = cryptoConfig;
        }

        @Provides
        CryptoConfig cryptoConfig() {
            return cryptoConfig;
        }

        @Provides
        @Singleton
        @Named(MAYBE_ENCRYPTION_BLOBSTORE)
        BlobStoreDAO provideEncryptionBlobStoreDAO(@Named(MAYBE_SECONDARY_BLOBSTORE) BlobStoreDAO blobStoreDAO) {
            return new AESBlobStoreDAO(blobStoreDAO, cryptoConfig);
        }
    }

    static class NoEncryptionModule extends AbstractModule {
        @Provides
        @Singleton
        @Named(MAYBE_ENCRYPTION_BLOBSTORE)
        BlobStoreDAO provideNoEncryptBlobStoreDAO(@Named(MAYBE_SECONDARY_BLOBSTORE) BlobStoreDAO blobStoreDAO) {
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
        @Named(MAYBE_SINGLE_SAVE_BLOBSTORE)
        public BlobStoreDAO provideSingleSaveBlobStoreDAO(@Named(MAYBE_ENCRYPTION_BLOBSTORE) BlobStoreDAO blobStoreDAO,
                                                          BlobIdList blobIdList,
                                                          BucketName defaultBucketName) {
            return new SingleSaveBlobStoreDAO(blobStoreDAO, blobIdList, defaultBucketName);
        }
    }

    static class MultiSaveDeclarationModule extends AbstractModule {

        @Provides
        @Singleton
        @Named(MAYBE_SINGLE_SAVE_BLOBSTORE)
        public BlobStoreDAO provideMultiSaveblobStoreDAO(@Named(MAYBE_ENCRYPTION_BLOBSTORE) BlobStoreDAO blobStoreDAO) {
            return blobStoreDAO;
        }
    }

    public static Module chooseSecondaryObjectStorageModule(Optional<SecondaryS3BlobStoreConfiguration> maybeSecondaryS3BlobStoreConfiguration) {
        return maybeSecondaryS3BlobStoreConfiguration
            .map(configuration -> (Module) new SecondaryObjectStorageModule(configuration))
            .orElse(new NoSecondaryObjectStorageModule());
    }

    public static Module chooseEncryptionModule(Optional<CryptoConfig> cryptoConfig) {
        return cryptoConfig
            .map(configuration -> (Module) new EncryptionModule(configuration))
            .orElse(new NoEncryptionModule());
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