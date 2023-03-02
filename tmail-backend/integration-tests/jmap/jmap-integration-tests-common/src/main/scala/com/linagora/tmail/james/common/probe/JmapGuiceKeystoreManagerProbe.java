package com.linagora.tmail.james.common.probe;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.utils.GuiceProbe;

import com.linagora.tmail.encrypted.KeyId;
import com.linagora.tmail.encrypted.KeystoreManager;
import com.linagora.tmail.encrypted.PublicKey;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class JmapGuiceKeystoreManagerProbe implements GuiceProbe {
    private final KeystoreManager keystore;

    @Inject
    public JmapGuiceKeystoreManagerProbe(KeystoreManager keystore) {
        this.keystore = keystore;
    }

    public Optional<PublicKey> retrieveKey(Username username, KeyId id) {
        try {
            return Optional.ofNullable(Mono.from(keystore.retrieveKey(username, id.value())).block());
        } catch (IllegalArgumentException e) {
           return Optional.empty();
        }
    }

    public List<byte[]> getKeyPayLoads(Username username) {
        return Flux.from(keystore.listPublicKeys(username))
            .map(PublicKey::key)
            .collectList()
            .block();
    }

    public void save(Username username, byte[] publicKeyPayload) {
        Mono.from(keystore.save(username, publicKeyPayload)).block();
    }
}
