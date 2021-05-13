package com.linagora.tmail.blob.blobid.list;

import org.apache.james.blob.api.BucketName;

import java.nio.charset.StandardCharsets;

public interface SingleSaveBlobStoreDAOFixture {
    BucketName TEST_BUCKET_NAME = BucketName.of("my-test-bucket");
    byte[] SHORT_BYTEARRAY = "SHORT_STRING".getBytes(StandardCharsets.UTF_8);
}
