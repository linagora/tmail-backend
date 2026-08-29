/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.migration.core;

import java.util.Optional;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.sasl.kerberos.GssapiSaslMechanismFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Optional Kerberos support: when present the proxy authenticates its IMAP clients with GSSAPI rather
 * than relaying their password, and {@code LOGIN} is disabled altogether.
 *
 * <p>Expected {@code migrationproxy.properties} keys:
 * <pre>
 *   kerberos.enabled      (default false: no Kerberos, clients authenticate with LOGIN)
 *   kerberos.serviceName  (e.g. imap)
 *   kerberos.serverName   (the host name clients use to reach the proxy)
 *   kerberos.principal    (must read as &lt;serviceName&gt;/&lt;serverName&gt;@REALM)
 *   kerberos.keyTab       (path to the keytab holding that principal)
 *   kerberos.requireSSL   (default true: GSSAPI is only offered over an encrypted transport)
 * </pre>
 *
 * <p>James reads the very same settings from {@code imapserver.xml} under {@code auth.gssapi}. We keep
 * the properties file as the single configuration entry point of the proxy but reuse the James stack
 * verbatim, hence {@link #asServerConfiguration()}.
 */
public record KerberosConfiguration(String serviceName, String serverName, String principal, String keyTab,
                                    boolean requireSSL) {
    public static final boolean ENABLED_DEFAULT = false;
    public static final boolean REQUIRE_SSL_DEFAULT = true;

    public KerberosConfiguration {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceName), "'kerberos.serviceName' should not be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(serverName), "'kerberos.serverName' should not be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(principal), "'kerberos.principal' should not be empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(keyTab), "'kerberos.keyTab' should not be empty");
    }

    public static Optional<KerberosConfiguration> from(Configuration configuration) {
        if (!configuration.getBoolean("kerberos.enabled", ENABLED_DEFAULT)) {
            return Optional.empty();
        }
        return Optional.of(new KerberosConfiguration(
            required(configuration, "kerberos.serviceName"),
            required(configuration, "kerberos.serverName"),
            required(configuration, "kerberos.principal"),
            required(configuration, "kerberos.keyTab"),
            configuration.getBoolean("kerberos.requireSSL", REQUIRE_SSL_DEFAULT)));
    }

    private static String required(Configuration configuration, String property) {
        String value = configuration.getString(property, null);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(value), "Missing required '%s' property", property);
        return value.trim();
    }

    /**
     * Builds the GSSAPI mechanism, which eagerly validates the principal against the keytab and acquires
     * the acceptor credentials: a misconfigured proxy fails to start rather than at first login.
     */
    public SaslMechanism saslMechanism() throws ConfigurationException {
        return new GssapiSaslMechanismFactory().create(asServerConfiguration());
    }

    /**
     * Renders these properties the way {@code GssapiSaslConfiguration} expects to read them off a James
     * server configuration.
     */
    HierarchicalConfiguration<ImmutableNode> asServerConfiguration() {
        BaseHierarchicalConfiguration serverConfiguration = new BaseHierarchicalConfiguration();
        serverConfiguration.addProperty("auth.gssapi.serviceName", serviceName);
        serverConfiguration.addProperty("auth.gssapi.serverName", serverName);
        serverConfiguration.addProperty("auth.gssapi.principal", principal);
        serverConfiguration.addProperty("auth.gssapi.keyTab", keyTab);
        serverConfiguration.addProperty("auth.requireSSL", requireSSL);
        return serverConfiguration;
    }
}
