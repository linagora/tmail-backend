package com.linagora.tmail.james.common.probe;

import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.encrypted.KeyId;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.PublicKey;

import reactor.core.publisher.Mono;

public class JmapGuiceKeystoreManagerProbe implements GuiceProbe {
    private final KeystoreManager keystore;

    @Inject
    public JmapGuiceKeystoreManagerProbe(KeystoreManager keystore) {
        this.keystore = keystore;
    }

    public Optional<PublicKey> retrieveKey(Username username, KeyId id) {
        try {
            return Optional.ofNullable(Mono.from(keystore.retrieveKey(username, id)).block());
        } catch (IllegalArgumentException e) {
           return Optional.empty();
        }
    }
}
