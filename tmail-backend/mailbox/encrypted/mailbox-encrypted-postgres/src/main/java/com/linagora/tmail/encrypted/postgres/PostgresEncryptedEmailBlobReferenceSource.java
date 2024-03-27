package com.linagora.tmail.encrypted.postgres;

import javax.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.reactivestreams.Publisher;

public class PostgresEncryptedEmailBlobReferenceSource implements BlobReferenceSource {
    private final PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO;

    @Inject
    public PostgresEncryptedEmailBlobReferenceSource(PostgresEncryptedEmailStoreDAO encryptedEmailStoreDAO) {
        this.encryptedEmailStoreDAO = encryptedEmailStoreDAO;
    }

    @Override
    public Publisher<BlobId> listReferencedBlobs() {
        return encryptedEmailStoreDAO.getAllBlobIds();
    }
}
