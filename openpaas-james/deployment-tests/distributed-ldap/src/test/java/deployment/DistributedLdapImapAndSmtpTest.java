package deployment;

import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

import com.linagora.openpaas.deployment.ImapAndSmtpContract;

public class DistributedLdapImapAndSmtpTest extends ImapAndSmtpContract {
    @RegisterExtension
    OpenpaasJamesDistributedLdapExtension extension = new OpenpaasJamesDistributedLdapExtension();

    @Override
    protected ExternalJamesConfiguration configuration() {
        return extension.configuration();
    }

    @Override
    protected GenericContainer<?> container() {
        return extension.getContainer();
    }
}
