import org.junit.jupiter.api.BeforeEach;

import com.linagora.openpaas.encrypted.InMemoryKeyId;
import com.linagora.openpaas.encrypted.InMemoryKeystoreManager;
import com.linagora.openpaas.encrypted.KeyId;
import com.linagora.openpaas.encrypted.KeystoreManager;
import com.linagora.openpaas.encrypted.KeystoreManagerContract;

import java.util.UUID;

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

    @Override
    public KeyId generateKey() {
        return new InMemoryKeyId(UUID.randomUUID());
    }
}
