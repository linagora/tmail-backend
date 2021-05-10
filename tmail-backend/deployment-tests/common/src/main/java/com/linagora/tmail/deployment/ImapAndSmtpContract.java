package com.linagora.tmail.deployment;

import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.DeploymentValidation;
import org.apache.james.mpt.imapmailbox.external.james.ExternalJamesModule;
import org.apache.james.mpt.imapmailbox.external.james.host.SmtpHostSystem;
import org.apache.james.mpt.imapmailbox.external.james.host.external.ExternalJamesConfiguration;
import org.apache.james.mpt.imapmailbox.external.james.host.external.NoopDomainsAndUserAdder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class ImapAndSmtpContract extends DeploymentValidation {
    protected abstract ExternalJamesConfiguration configuration();

    protected abstract GenericContainer<?> container();

    private ImapHostSystem system;
    private SmtpHostSystem smtpHostSystem;
    private ExternalJamesConfiguration configuration;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        container().execInContainer("james-cli", "AddDomain", "domain");
        container().execInContainer("james-cli", "AddUser", "imapuser@domain", "password");

        configuration = configuration();
        Injector injector = Guice.createInjector(new ExternalJamesModule(configuration, new NoopDomainsAndUserAdder()));
        system = injector.getInstance(ImapHostSystem.class);
        smtpHostSystem = injector.getInstance(SmtpHostSystem.class);
        system.beforeTest();
        super.setUp();
    }

    @Override
    protected ImapHostSystem createImapHostSystem() {
        return system;
    }

    @Override
    protected SmtpHostSystem createSmtpHostSystem() {
        return smtpHostSystem;
    }

    @Override
    protected ExternalJamesConfiguration getConfiguration() {
        return configuration;
    }

    @AfterEach
    public void tearDown() throws Exception {
        system.afterTest();
    }
}
