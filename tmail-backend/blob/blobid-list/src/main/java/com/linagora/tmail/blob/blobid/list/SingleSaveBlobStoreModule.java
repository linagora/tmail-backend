package com.linagora.tmail.blob.blobid.list;

import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.objectstorage.aws.S3BlobStoreDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.blobid.list.cassandra.BlobIdListCassandraModule;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListModule;

public class SingleSaveBlobStoreModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new BlobIdListCassandraModule());

        Multibinder.newSetBinder(binder(), CassandraModule.class)
            .addBinding()
            .toInstance(CassandraBlobIdListModule.MODULE());
    }

    @Provides
    private SingleSaveBlobStoreDAO provideSingleSaveBlobStoreDAO(S3BlobStoreDAO s3BlobStoreDAO,
                                                              BlobIdList blobIdList,
                                                              BucketName defaultBucketName) {
        return new SingleSaveBlobStoreDAO(s3BlobStoreDAO, blobIdList, defaultBucketName);
    }
}
