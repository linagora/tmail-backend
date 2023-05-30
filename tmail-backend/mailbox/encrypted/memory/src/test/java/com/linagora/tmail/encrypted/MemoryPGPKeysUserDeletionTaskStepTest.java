package com.linagora.tmail.encrypted;

import org.junit.jupiter.api.BeforeEach;

public class MemoryPGPKeysUserDeletionTaskStepTest implements PGPKeysUserDeletionTaskStepContract {
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
    public PGPKeysUserDeletionTaskStep testee() {
        return new PGPKeysUserDeletionTaskStep(keystoreManager);
    }
}
