package com.linagora.openpaas.james.app;

import org.apache.james.DockerLdapRule;
import org.apache.james.GuiceModuleTestExtension;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class LdapTestExtension implements GuiceModuleTestExtension {

    private DockerLdapRule ldapRule;

    LdapTestExtension() {
        this(new DockerLdapRule());
    }

    LdapTestExtension(DockerLdapRule ldapRule) {
        this.ldapRule = ldapRule;
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        ldapRule.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        ldapRule.stop();
    }

    @Override
    public Module getModule() {
        return ldapRule.getModule();
    }
}
