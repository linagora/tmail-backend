package com.linagora.tmail.encrypted;

import org.junit.jupiter.api.BeforeEach;

public class MemoryPGPKeysUsernameChangeTaskStepTest implements PGPKeysUsernameChangeTaskStepContract {
    private KeystoreManager keystoreManager;

    @BeforeEach
    void setUp() {
        keystoreManager = new InMemoryKeystoreManager();
    }

    @Override
    public KeystoreManager keyStoreManager() {
        return keystoreManager;
    }

    @Override
    public PGPKeysUsernameChangeTaskStep testee() {
        return new PGPKeysUsernameChangeTaskStep(keystoreManager);
    }
}
