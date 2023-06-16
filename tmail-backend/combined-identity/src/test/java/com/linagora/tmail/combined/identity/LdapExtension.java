package com.linagora.tmail.combined.identity;

import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN;
import static org.apache.james.user.ldap.DockerLdapSingleton.ADMIN_PASSWORD;

import java.util.function.Function;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.plist.PropertyListConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.GuiceModuleTestExtension;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.user.ldap.DockerLdapSingleton;
import org.apache.james.user.ldap.LdapGenericContainer;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.google.inject.Module;

public class LdapExtension implements GuiceModuleTestExtension {
    private final LdapGenericContainer ldapGenericContainer = DockerLdapSingleton.ldapContainer;

    private final Function<String, HierarchicalConfiguration<ImmutableNode>> configurationFunction;

    public LdapExtension(Function<String, HierarchicalConfiguration<ImmutableNode>> configurationFunction) {
        this.configurationFunction = configurationFunction;
    }

    public LdapExtension() {
        this.configurationFunction = LdapExtension::baseConfiguration;
    }
    @Override
    public Module getModule() {
        return binder -> binder.bind(ConfigurationProvider.class)
            .toInstance((s, l) -> new BaseHierarchicalConfiguration(configurationFunction.apply(ldapGenericContainer.getLdapHost())));
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) {
        ldapGenericContainer.start();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) {
        ldapGenericContainer.stop();
    }

    private static HierarchicalConfiguration<ImmutableNode> baseConfiguration(String ldapHost) {
        PropertyListConfiguration configuration = new PropertyListConfiguration();
        configuration.addProperty("[@ldapHost]", ldapHost);
        configuration.addProperty("[@principal]", "cn=admin,dc=james,dc=org");
        configuration.addProperty("[@credentials]", ADMIN_PASSWORD);
        configuration.addProperty("[@userBase]", "ou=People,dc=james,dc=org");
        configuration.addProperty("[@userObjectClass]", "inetOrgPerson");
        configuration.addProperty("[@maxRetries]", "1");
        configuration.addProperty("[@retryStartInterval]", "0");
        configuration.addProperty("[@retryMaxInterval]", "2");
        configuration.addProperty("[@retryIntervalScale]", "1000");
        configuration.addProperty("[@connectionTimeout]", "1000");
        configuration.addProperty("[@readTimeout]", "1000");

        configuration.addProperty("[@userIdAttribute]", "mail");
        configuration.addProperty("[@administratorId]", ADMIN.asString());
        configuration.addProperty("enableVirtualHosting", true);
        return configuration;
    }

}
