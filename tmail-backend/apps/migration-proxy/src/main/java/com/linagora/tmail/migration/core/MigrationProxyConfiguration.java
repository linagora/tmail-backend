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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.util.DurationParser;
import org.apache.james.util.Host;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Resolves the old and new IMAP backend the proxy relays connections to (the SMTP old/new/external
 * routing lives in {@code mailetcontainer.xml} instead).
 *
 * <p>Expected {@code migrationproxy.properties} keys (per {@code <target>} in {@code old}, {@code new}):
 * <pre>
 *   imap.&lt;target&gt;.host
 *   imap.&lt;target&gt;.port
 *   imap.&lt;target&gt;.ssl                    (default false: implicit TLS to the backend)
 *   imap.&lt;target&gt;.ssl.ignoreCertificates  (default false: trust self-signed backend certs)
 *   imap.&lt;target&gt;.forwardProxyInfo        (default false: forward the inbound PROXY protocol info)
 *   imap.&lt;target&gt;.admin.username           (required when Kerberos is enabled)
 *   imap.&lt;target&gt;.admin.password           (required when Kerberos is enabled)
 * </pre>
 *
 * <p>An optional {@code imap.handshakeTimeout} (default {@code 30s}) bounds how long the proxy waits
 * while connecting to and replaying the authentication against a backend before giving up on a LOGIN.
 *
 * <p>See {@link KerberosConfiguration} for the optional {@code kerberos.} settings.
 */
public record MigrationProxyConfiguration(Backend imapOld, Backend imapNew, Duration handshakeTimeout,
                                          Optional<KerberosConfiguration> kerberos) {
    public static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(30);

    public MigrationProxyConfiguration {
        Preconditions.checkNotNull(imapOld);
        Preconditions.checkNotNull(imapNew);
        Preconditions.checkNotNull(handshakeTimeout);
        Preconditions.checkNotNull(kerberos);
        if (kerberos.isPresent()) {
            // Kerberos authenticated clients never hand us their password: the only way into a backend is
            // then delegation from a configured administrator.
            requireAdmin(imapOld);
            requireAdmin(imapNew);
        }
    }

    private static void requireAdmin(Backend backend) {
        Preconditions.checkArgument(backend.admin().isPresent(),
            "Kerberos requires 'imap.%s.admin.username' and 'imap.%s.admin.password' to be set",
            backend.name(), backend.name());
    }

    public static MigrationProxyConfiguration from(Configuration configuration) {
        return new MigrationProxyConfiguration(
            readBackend(configuration, Target.OLD),
            readBackend(configuration, Target.NEW),
            readHandshakeTimeout(configuration),
            KerberosConfiguration.from(configuration));
    }

    private static Duration readHandshakeTimeout(Configuration configuration) {
        return Optional.ofNullable(configuration.getString("imap.handshakeTimeout", null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS))
            .orElse(DEFAULT_HANDSHAKE_TIMEOUT);
    }

    private static Backend readBackend(Configuration configuration, Target target) {
        String prefix = "imap." + target.asString();
        String host = configuration.getString(prefix + ".host", null);
        Preconditions.checkArgument(host != null, "Missing required '%s.host' property", prefix);
        int port = configuration.getInt(prefix + ".port", 143);
        boolean ssl = configuration.getBoolean(prefix + ".ssl", false);
        boolean ignoreCertificates = configuration.getBoolean(prefix + ".ssl.ignoreCertificates", false);
        boolean forwardProxyInfo = configuration.getBoolean(prefix + ".forwardProxyInfo", false);
        return new Backend(target.asString(), Host.from(host, port), ssl, ignoreCertificates, forwardProxyInfo,
            readAdminCredentials(configuration, prefix));
    }

    private static Optional<AdminCredentials> readAdminCredentials(Configuration configuration, String prefix) {
        return readOptional(configuration, prefix + ".admin.username")
            .map(username -> new AdminCredentials(username,
                readOptional(configuration, prefix + ".admin.password").orElse(null)));
    }

    private static Optional<String> readOptional(Configuration configuration, String property) {
        return Optional.ofNullable(Strings.emptyToNull(configuration.getString(property, null)));
    }

    public Backend backend(Target target) {
        return target == Target.OLD ? imapOld : imapNew;
    }

    public enum Target {
        OLD("old"),
        NEW("new");

        private final String value;

        Target(String value) {
            this.value = value;
        }

        public String asString() {
            return value;
        }
    }
}
