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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.Test;

class MigrationProxyConfigurationTest {
    private static Configuration backends() {
        Configuration configuration = new BaseConfiguration();
        configuration.addProperty("imap.old.host", "old-backend");
        configuration.addProperty("imap.new.host", "new-backend");
        return configuration;
    }

    private static Configuration kerberised() {
        Configuration configuration = backends();
        configuration.addProperty("kerberos.enabled", "true");
        configuration.addProperty("kerberos.serviceName", "imap");
        configuration.addProperty("kerberos.serverName", "imap.domain.tld");
        configuration.addProperty("kerberos.principal", "imap/imap.domain.tld@DOMAIN.TLD");
        configuration.addProperty("kerberos.keyTab", "/root/conf/imap.keytab");
        configuration.addProperty("imap.old.admin.username", "old-admin@domain.tld");
        configuration.addProperty("imap.old.admin.password", "old-password");
        configuration.addProperty("imap.new.admin.username", "new-admin@domain.tld");
        configuration.addProperty("imap.new.admin.password", "new-password");
        return configuration;
    }

    @Test
    void kerberosShouldBeDisabledByDefault() {
        assertThat(MigrationProxyConfiguration.from(backends()).kerberos()).isEmpty();
    }

    @Test
    void shouldNotReadKerberosSettingsWhenDisabled() {
        Configuration configuration = kerberised();
        configuration.setProperty("kerberos.enabled", "false");

        assertThat(MigrationProxyConfiguration.from(configuration).kerberos()).isEmpty();
    }

    @Test
    void shouldReadKerberosSettings() {
        assertThat(MigrationProxyConfiguration.from(kerberised()).kerberos())
            .contains(new KerberosConfiguration("imap", "imap.domain.tld", "imap/imap.domain.tld@DOMAIN.TLD",
                "/root/conf/imap.keytab", true));
    }

    @Test
    void requireSSLShouldDefaultToTrue() {
        assertThat(MigrationProxyConfiguration.from(kerberised()).kerberos().map(KerberosConfiguration::requireSSL))
            .contains(true);
    }

    @Test
    void shouldReadRequireSSL() {
        Configuration configuration = kerberised();
        configuration.addProperty("kerberos.requireSSL", "false");

        assertThat(MigrationProxyConfiguration.from(configuration).kerberos().map(KerberosConfiguration::requireSSL))
            .contains(false);
    }

    @Test
    void shouldThrowWhenKerberosSettingIsMissing() {
        Configuration configuration = kerberised();
        configuration.clearProperty("kerberos.principal");

        assertThatThrownBy(() -> MigrationProxyConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kerberos.principal");
    }

    @Test
    void shouldReadAdminCredentials() {
        assertThat(MigrationProxyConfiguration.from(kerberised()).imapNew().admin())
            .contains(new AdminCredentials("new-admin@domain.tld", "new-password"));
    }

    @Test
    void adminCredentialsShouldBeOptionalWithoutKerberos() {
        assertThat(MigrationProxyConfiguration.from(backends()).imapOld().admin()).isEmpty();
    }

    @Test
    void shouldThrowWhenKerberosIsEnabledWithoutBackendAdmin() {
        Configuration configuration = kerberised();
        configuration.clearProperty("imap.old.admin.username");

        assertThatThrownBy(() -> MigrationProxyConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("imap.old.admin.username");
    }

    @Test
    void shouldThrowWhenAdminPasswordIsMissing() {
        Configuration configuration = kerberised();
        configuration.clearProperty("imap.new.admin.password");

        assertThatThrownBy(() -> MigrationProxyConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Admin password");
    }

    @Test
    void emptyAdminSettingsShouldReadAsAbsent() {
        Configuration configuration = backends();
        configuration.addProperty("imap.old.admin.username", "");
        configuration.addProperty("imap.old.admin.password", "");

        assertThatCode(() -> MigrationProxyConfiguration.from(configuration)).doesNotThrowAnyException();
        assertThat(MigrationProxyConfiguration.from(configuration).imapOld().admin()).isEqualTo(Optional.empty());
    }
}
