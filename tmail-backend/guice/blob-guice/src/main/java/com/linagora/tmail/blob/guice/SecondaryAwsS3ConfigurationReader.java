package com.linagora.tmail.blob.guice;

import java.net.URI;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.blob.objectstorage.aws.AwsS3AuthConfiguration;

public class SecondaryAwsS3ConfigurationReader {
    static final String OBJECTSTORAGE_ENDPOINT = "objectstorage.s3.secondary.endPoint";
    static final String OBJECTSTORAGE_ACCESKEYID = "objectstorage.s3.secondary.accessKeyId";
    static final String OBJECTSTORAGE_SECRETKEY = "objectstorage.s3.secondary.secretKey";
    static final String OBJECTSTORAGE_TRUSTSTORE_PATH = "objectstorage.s3.truststore.path";
    static final String OBJECTSTORAGE_TRUSTSTORE_TYPE = "objectstorage.s3.truststore.type";
    static final String OBJECTSTORAGE_TRUSTSTORE_SECRET = "objectstorage.s3.truststore.secret";
    static final String OBJECTSTORAGE_TRUSTSTORE_ALGORITHM = "objectstorage.s3.truststore.algorithm";
    static final String OBJECTSTORAGE_TRUSTALL = "objectstorage.s3.trustall";

    public static AwsS3AuthConfiguration from(Configuration configuration) {
        String endpoint = configuration.getString(OBJECTSTORAGE_ENDPOINT);
        if (StringUtils.isEmpty(endpoint)) {
            throw new NullPointerException("'endpoint' is mandatory");
        }

        return AwsS3AuthConfiguration.builder()
                .endpoint(URI.create(endpoint))
                .accessKeyId(configuration.getString(OBJECTSTORAGE_ACCESKEYID))
                .secretKey(configuration.getString(OBJECTSTORAGE_SECRETKEY))
                .trustStorePath(configuration.getString(OBJECTSTORAGE_TRUSTSTORE_PATH))
                .trustStoreType(configuration.getString(OBJECTSTORAGE_TRUSTSTORE_TYPE))
                .trustStoreSecret(configuration.getString(OBJECTSTORAGE_TRUSTSTORE_SECRET))
                .trustStoreAlgorithm(configuration.getString(OBJECTSTORAGE_TRUSTSTORE_ALGORITHM))
                .trustAll(configuration.getBoolean(OBJECTSTORAGE_TRUSTALL, false))
                .build();
    }
}

