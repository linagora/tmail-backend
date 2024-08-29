package com.linagora.tmail.james.app;

import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.blob.objectstorage.aws.DockerAwsS3Singleton;
import org.apache.james.modules.objectstorage.aws.s3.DockerAwsS3TestRule;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class AwsS3BlobStoreExtension implements GuiceModuleTestExtension {

    private final DockerAwsS3TestRule awsS3TestRule;

    public AwsS3BlobStoreExtension() {
        this.awsS3TestRule = new DockerAwsS3TestRule();
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        ensureAwsS3started();
    }

    private void ensureAwsS3started() {
        DockerAwsS3Singleton.singleton.dockerAwsS3();
    }

    @Override
    public Module getModule() {
        return awsS3TestRule.getModule();
    }
}
