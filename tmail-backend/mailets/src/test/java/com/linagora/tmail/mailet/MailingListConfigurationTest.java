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

package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.junit.jupiter.api.Test;

class MailingListConfigurationTest {
    @Test
    void shouldReadBaseDnMailAttributeAndObmFlag() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("baseDN", "ou=lists,dc=linagora,dc=com");
        configuration.addProperty("mailAttributeForGroups", "description");
        configuration.addProperty("obm.compatibility", "true");

        MailingListConfiguration result = MailingListConfiguration.from(configuration);

        assertThat(result).isEqualTo(new MailingListConfiguration(
            Optional.of("ou=lists,dc=linagora,dc=com"), "description", true));
    }

    @Test
    void shouldReadFullBaseDnWhenCommaSplittingIsEnabled() {
        // James loads .properties files with a comma list-delimiter: a DN must not be truncated to its first component.
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        configuration.addProperty("baseDN", "ou=lists,dc=linagora,dc=com");

        MailingListConfiguration result = MailingListConfiguration.from(configuration);

        assertThat(result.baseDN()).contains("ou=lists,dc=linagora,dc=com");
    }

    @Test
    void shouldReadFullBaseDnWhenCommasAreEscaped() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.setListDelimiterHandler(new DefaultListDelimiterHandler(','));
        configuration.addProperty("baseDN", "ou=lists\\,dc=linagora\\,dc=com");

        MailingListConfiguration result = MailingListConfiguration.from(configuration);

        assertThat(result.baseDN()).contains("ou=lists,dc=linagora,dc=com");
    }

    @Test
    void mailAttributeForGroupsShouldDefaultToMail() {
        MailingListConfiguration result = MailingListConfiguration.from(new PropertiesConfiguration());

        assertThat(result.mailAttributeForGroups()).isEqualTo("mail");
    }

    @Test
    void baseDnShouldBeEmptyWhenMissing() {
        MailingListConfiguration result = MailingListConfiguration.from(new PropertiesConfiguration());

        assertThat(result.baseDN()).isEmpty();
    }

    @Test
    void baseDnShouldBeEmptyWhenBlank() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("baseDN", "  ");

        assertThat(MailingListConfiguration.from(configuration).baseDN()).isEmpty();
    }

    @Test
    void obmCompatibilityShouldDefaultToFalse() {
        MailingListConfiguration result = MailingListConfiguration.from(new PropertiesConfiguration());

        assertThat(result.obmCompatibility()).isFalse();
    }

    @Test
    void resolveBaseDnShouldPreferComponentValue() {
        MailingListConfiguration configuration = new MailingListConfiguration(
            Optional.of("ou=centralized,dc=james,dc=org"), "mail", false);

        assertThat(configuration.resolveBaseDN(Optional.of("ou=component,dc=james,dc=org")))
            .contains("ou=component,dc=james,dc=org");
    }

    @Test
    void resolveBaseDnShouldFallbackToCentralizedValue() {
        MailingListConfiguration configuration = new MailingListConfiguration(
            Optional.of("ou=centralized,dc=james,dc=org"), "mail", false);

        assertThat(configuration.resolveBaseDN(Optional.empty()))
            .contains("ou=centralized,dc=james,dc=org");
    }
}
