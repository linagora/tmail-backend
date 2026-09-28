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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.migration.core.MigrationProxyConfiguration.Target;

class SubmissionProxyConfigurationTest {
    private static Configuration backends() {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("submission.old.host", "old-backend");
        configuration.addProperty("submission.new.host", "new-backend");
        return configuration;
    }

    @Test
    void shouldBeDisabledByDefault() {
        assertThat(SubmissionProxyConfiguration.isEnabled(new BaseConfiguration())).isFalse();
    }

    @Test
    void shouldBeEnabledWhenRequested() {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("submission.enabled", "true");

        assertThat(SubmissionProxyConfiguration.isEnabled(configuration)).isTrue();
    }

    @Test
    void shouldApplyDefaults() {
        SubmissionProxyConfiguration configuration = SubmissionProxyConfiguration.from(backends());

        assertThat(configuration.backend(Target.OLD).host().getHostName()).isEqualTo("old-backend");
        assertThat(configuration.backend(Target.OLD).host().getPort()).isEqualTo(587);
        assertThat(configuration.backend(Target.OLD).ssl()).isFalse();
        assertThat(configuration.backend(Target.NEW).host().getHostName()).isEqualTo("new-backend");
        assertThat(configuration.backend(Target.NEW).host().getPort()).isEqualTo(587);
        assertThat(configuration.handshakeTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(configuration.requireSSL()).isFalse();
        assertThat(configuration.ehloExtensions()).isEmpty();
    }

    @Test
    void shouldReadExplicitSettings() {
        Configuration properties = backends();
        properties.addProperty("submission.old.port", "465");
        properties.addProperty("submission.old.ssl", "true");
        properties.addProperty("submission.old.ssl.ignoreCertificates", "true");
        properties.addProperty("submission.new.forwardProxyInfo", "true");
        properties.addProperty("submission.handshakeTimeout", "5s");
        properties.addProperty("submission.auth.requireSSL", "true");

        SubmissionProxyConfiguration configuration = SubmissionProxyConfiguration.from(properties);

        assertThat(configuration.backend(Target.OLD).host().getPort()).isEqualTo(465);
        assertThat(configuration.backend(Target.OLD).ssl()).isTrue();
        assertThat(configuration.backend(Target.OLD).sslIgnoreCertificates()).isTrue();
        assertThat(configuration.backend(Target.NEW).forwardProxyInfo()).isTrue();
        assertThat(configuration.handshakeTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(configuration.requireSSL()).isTrue();
    }

    @Test
    void shouldNotShareTheImapBackendSettings() {
        Configuration properties = backends();
        properties.addProperty("imap.old.host", "imap-old");
        properties.addProperty("imap.old.port", "143");

        assertThat(SubmissionProxyConfiguration.from(properties).backend(Target.OLD).host().getHostName())
            .isEqualTo("old-backend");
    }

    @ParameterizedTest
    @ValueSource(strings = {"old", "new"})
    void shouldRequireBothBackends(String target) {
        Configuration properties = backends();
        properties.clearProperty("submission." + target + ".host");

        assertThatThrownBy(() -> SubmissionProxyConfiguration.from(properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("submission." + target + ".host");
    }

    @Test
    void shouldReadEhloExtensions() {
        Configuration properties = backends();
        properties.addProperty("submission.ehlo.extensions", " SIZE 52428800 , SMTPUTF8,, DSN ");

        assertThat(SubmissionProxyConfiguration.from(properties).ehloExtensions())
            .containsExactly("SIZE 52428800", "SMTPUTF8", "DSN");
    }

    @Test
    void shouldReadEhloExtensionsAlreadySplitByTheConfiguration() {
        BaseConfiguration properties = new BaseConfiguration();
        properties.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        properties.addProperty("submission.old.host", "old-backend");
        properties.addProperty("submission.new.host", "new-backend");
        properties.addProperty("submission.ehlo.extensions", "SIZE 52428800, SMTPUTF8");

        assertThat(SubmissionProxyConfiguration.from(properties).ehloExtensions())
            .containsExactly("SIZE 52428800", "SMTPUTF8");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTH PLAIN", "auth=PLAIN", "STARTTLS", "starttls"})
    void shouldRejectKeywordsTheProxyAnnouncesItself(String extension) {
        Configuration properties = backends();
        properties.addProperty("submission.ehlo.extensions", extension);

        assertThatThrownBy(() -> SubmissionProxyConfiguration.from(properties))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
