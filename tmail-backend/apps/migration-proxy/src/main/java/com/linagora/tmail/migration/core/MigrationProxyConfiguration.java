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
 * </pre>
 *
 * <p>An optional {@code imap.handshakeTimeout} (default {@code 30s}) bounds how long the proxy waits
 * while connecting to and replaying the authentication against a backend before giving up on a LOGIN.
 */
public record MigrationProxyConfiguration(Backend imapOld, Backend imapNew, Duration handshakeTimeout) {
    public static final Duration DEFAULT_HANDSHAKE_TIMEOUT = Duration.ofSeconds(30);

    public MigrationProxyConfiguration {
        Preconditions.checkNotNull(imapOld);
        Preconditions.checkNotNull(imapNew);
        Preconditions.checkNotNull(handshakeTimeout);
    }

    public static MigrationProxyConfiguration from(Configuration configuration) {
        return new MigrationProxyConfiguration(
            readBackend(configuration, Target.OLD),
            readBackend(configuration, Target.NEW),
            readHandshakeTimeout(configuration));
    }

    private static Duration readHandshakeTimeout(Configuration configuration) {
        return Optional.ofNullable(configuration.getString(Protocol.IMAP.asString() + ".handshakeTimeout", null))
            .map(value -> DurationParser.parse(value, ChronoUnit.SECONDS))
            .orElse(DEFAULT_HANDSHAKE_TIMEOUT);
    }

    private static Backend readBackend(Configuration configuration, Target target) {
        String prefix = Protocol.IMAP.asString() + "." + target.asString();
        String host = configuration.getString(prefix + ".host", null);
        Preconditions.checkArgument(host != null, "Missing required '%s.host' property", prefix);
        int port = configuration.getInt(prefix + ".port", 143);
        boolean ssl = configuration.getBoolean(prefix + ".ssl", false);
        boolean ignoreCertificates = configuration.getBoolean(prefix + ".ssl.ignoreCertificates", false);
        boolean forwardProxyInfo = configuration.getBoolean(prefix + ".forwardProxyInfo", false);
        return new Backend(target.asString(), Host.from(host, port), ssl, ignoreCertificates, forwardProxyInfo);
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
