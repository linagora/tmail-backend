package deployment;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import com.linagora.openpaas.deployment.CliContract;

public class DistributedLdapCliTest implements CliContract {
    @RegisterExtension
    OpenpaasJamesDistributedLdapExtension extension = new OpenpaasJamesDistributedLdapExtension();

    @Override
    public GenericContainer<?> jamesContainer() {
        return extension.getContainer();
    }
}
