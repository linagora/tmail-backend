import org.junit.jupiter.api.BeforeEach;

import com.linagora.openpaas.encrypted.InMemoryKeystoreManager;
import com.linagora.openpaas.encrypted.KeystoreManager;
import com.linagora.openpaas.encrypted.KeystoreManagerContract;

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
