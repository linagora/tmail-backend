package com.linagora.tmail.james.common.probe;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.MessageId;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.encrypted.EncryptedEmailContentStore;

import reactor.core.publisher.Mono;

public class JmapGuiceEncryptedEmailContentStoreProbe implements GuiceProbe {
    private final EncryptedEmailContentStore encryptedEmailContentStore;

    @Inject
    public JmapGuiceEncryptedEmailContentStoreProbe(EncryptedEmailContentStore encryptedEmailContentStore) {
        this.encryptedEmailContentStore = encryptedEmailContentStore;
    }

    public void delete(MessageId messageId) {
        Mono.from(encryptedEmailContentStore.delete(messageId)).block();
    }
}
