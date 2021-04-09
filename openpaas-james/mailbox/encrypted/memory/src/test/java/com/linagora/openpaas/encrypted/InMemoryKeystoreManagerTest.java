package com.linagora.openpaas.encrypted;

import org.junit.jupiter.api.BeforeEach;

public class InMemoryKeystoreManagerTest implements KeystoreManagerContract {

    private KeystoreManager store;

    @BeforeEach
    void setUp() {
        store = new InMemoryKeystoreManager();
    }

    @Override
    public KeystoreManager keyStoreManager() {
        return store;
    }
}
