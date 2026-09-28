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


package com.linagora.tmail.migration.submission;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.configuration2.Configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.migration.core.Backend;
import com.linagora.tmail.migration.core.MigrationProxyConfiguration;
import com.linagora.tmail.migration.core.MigrationProxyConfiguration.Target;

/**
 * Settings of the optional SMTP submission proxy, read from {@code migrationproxy.properties}.
 *
 * <p>The submission proxy is opt-in ({@code submission.enabled}, default {@code false}). When enabled,
 * the listeners declared in {@code submissionproxy.xml} authenticate their clients on behalf of the
 * submission port of the backend the user belongs to, then relay the connection raw, as the IMAP proxy
 * does. Expected keys (per {@code <target>} in {@code old}, {@code new}):
 * <pre>
 *   submission.&lt;target&gt;.host
 *   submission.&lt;target&gt;.port                    (default 587)
 *   submission.&lt;target&gt;.ssl                     (default false: implicit TLS to the backend)
 *   submission.&lt;target&gt;.ssl.ignoreCertificates  (default false: validate backend certificates)
 *   submission.&lt;target&gt;.forwardProxyInfo        (default false: forward the inbound PROXY protocol info)
 * </pre>
 *
 * <p>Optional keys:
 * <ul>
 *   <li>{@code submission.handshakeTimeout} (default {@code 30s}) bounds how long the proxy waits while
 *   connecting to and authenticating against a backend before failing an {@code AUTH};</li>
 *   <li>{@code submission.auth.requireSSL} (default {@code false}) only offers and accepts {@code AUTH}
 *   over an encrypted client connection;</li>
 *   <li>{@code submission.ehlo.extensions} is a comma separated list of extra ESMTP keywords to
 *   advertise, e.g. {@code SIZE 52428800, SMTPUTF8}. The client never sees the backend's own EHLO
 *   response, so these must be supported by both backends.</li>
 * </ul>
 */
public record SubmissionProxyConfiguration(Backend submissionOld, Backend submissionNew, Duration handshakeTimeout,
                                           boolean requireSSL, ImmutableList<String> ehloExtensions) {
    public static final String PROTOCOL = "submission";
    public static final String ENABLED_PROPERTY = PROTOCOL + ".enabled";
    public static final boolean ENABLED_DEFAULT = false;
    public static final boolean REQUIRE_SSL_DEFAULT = false;
    private static final int DEFAULT_PORT = 587;
    // Keywords whose announcement depends on the proxy session state rather than on a static list.
    private static final ImmutableList<String> RESERVED_KEYWORDS = ImmutableList.of("AUTH", "STARTTLS");

    public SubmissionProxyConfiguration {
        Preconditions.checkNotNull(submissionOld);
        Preconditions.checkNotNull(submissionNew);
        Preconditions.checkNotNull(handshakeTimeout);
        Preconditions.checkNotNull(ehloExtensions);
        ehloExtensions.forEach(SubmissionProxyConfiguration::checkExtension);
    }

    public static boolean isEnabled(Configuration configuration) {
        return configuration.getBoolean(ENABLED_PROPERTY, ENABLED_DEFAULT);
    }

    public static SubmissionProxyConfiguration from(Configuration configuration) {
        return new SubmissionProxyConfiguration(
            MigrationProxyConfiguration.readBackend(configuration, PROTOCOL, Target.OLD, DEFAULT_PORT),
            MigrationProxyConfiguration.readBackend(configuration, PROTOCOL, Target.NEW, DEFAULT_PORT),
            MigrationProxyConfiguration.readHandshakeTimeout(configuration, PROTOCOL),
            configuration.getBoolean(PROTOCOL + ".auth.requireSSL", REQUIRE_SSL_DEFAULT),
            readEhloExtensions(configuration));
    }

    private static ImmutableList<String> readEhloExtensions(Configuration configuration) {
        // Split ourselves: whether commons-configuration already split the value on commas depends on the
        // list delimiter handler of the loaded file.
        return configuration.getList(String.class, PROTOCOL + ".ehlo.extensions", List.of()).stream()
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .filter(extension -> !extension.isEmpty())
            .collect(ImmutableList.toImmutableList());
    }

    private static void checkExtension(String extension) {
        Preconditions.checkArgument(!extension.isBlank() && extension.equals(extension.trim()),
            "Invalid '%s.ehlo.extensions' entry: '%s'", PROTOCOL, extension);
        Preconditions.checkArgument(extension.chars().noneMatch(c -> c == '\r' || c == '\n'),
            "'%s.ehlo.extensions' entries must fit on a single line", PROTOCOL);
        // "AUTH=PLAIN" is the legacy form of "AUTH PLAIN".
        String keyword = extension.split("[ =]")[0].toUpperCase(Locale.US);
        Preconditions.checkArgument(!RESERVED_KEYWORDS.contains(keyword),
            "'%s' is announced by the submission proxy itself and cannot be listed in '%s.ehlo.extensions'",
            keyword, PROTOCOL);
    }

    public Backend backend(Target target) {
        return target == Target.OLD ? submissionOld : submissionNew;
    }
}
