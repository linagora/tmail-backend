package deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import com.linagora.openpaas.deployment.JmapContract;

public class DistributedLdapJmapTest implements JmapContract {
    @RegisterExtension
    OpenpaasJamesDistributedLdapExtension extension = new OpenpaasJamesDistributedLdapExtension();

    @Override
    public GenericContainer<?> jmapContainer() {
        return extension.getContainer();
    }
}
