package com.linagora.openpaas.james.common.probe;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.openpaas.encrypted.KeyId;
import com.linagora.openpaas.encrypted.KeystoreManager;
import com.linagora.openpaas.encrypted.PublicKey;

import reactor.core.publisher.Mono;

public class JmapGuiceKeystoreManagerProbe implements GuiceProbe {
    private final KeystoreManager keystore;

    @Inject
    public JmapGuiceKeystoreManagerProbe(KeystoreManager keystore) {
        this.keystore = keystore;
    }

    public PublicKey retrieveKey(Username username, KeyId id) {
        return Mono.from(keystore.retrieveKey(username, id)).block();
    }
}
