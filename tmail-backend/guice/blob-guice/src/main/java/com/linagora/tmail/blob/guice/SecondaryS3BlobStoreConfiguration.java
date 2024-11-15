package com.linagora.tmail.blob.guice;

import org.apache.james.blob.objectstorage.aws.S3BlobStoreConfiguration;

public record SecondaryS3BlobStoreConfiguration(S3BlobStoreConfiguration s3BlobStoreConfiguration, String secondaryBucketSuffix) {
}
