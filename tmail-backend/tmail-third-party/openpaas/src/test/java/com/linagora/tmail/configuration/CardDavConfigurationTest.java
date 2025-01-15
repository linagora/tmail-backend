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

package com.linagora.tmail.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class CardDavConfigurationTest {

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(DavConfiguration.class)
            .verify();
    }

    @Test
    void fromShouldReturnTheConfigurationWhenAllParametersAreGiven() throws URISyntaxException {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("carddav.api.uri", "http://localhost:8080");
        configuration.addProperty("carddav.admin.user", "jhon_doe");
        configuration.addProperty("carddav.admin.password", "123");
        configuration.addProperty("carddav.rest.client.trust.all.ssl.certs", "true");
        configuration.addProperty("carddav.rest.client.response.timeout", "500");

        DavConfiguration expected = new DavConfiguration(
            new UsernamePasswordCredentials("jhon_doe", "123"),
            new URI("http://localhost:8080"),
            Optional.of(true)
            , Optional.of(Duration.ofMillis(500)));

        assertThat(DavConfiguration.from(configuration))
            .isEqualTo(expected);
    }

    @Test
    void fromShouldThrowWhenCardDavApiUriNotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("carddav.admin.user", "jhon_doe");
        configuration.addProperty("carddav.admin.password", "123");

        assertThatThrownBy(() -> DavConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("carddav.api.uri should not be empty");
    }

    @Test
    void fromShouldThrowWhenCardDavAdminUserNotConfigured() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("carddav.api.uri", "http://localhost:8080");
        configuration.addProperty("carddav.admin.password", "123");
        configuration.addProperty("carddav.rest.client.trust.all.ssl.certs", "true");
        configuration.addProperty("carddav.rest.client.response.timeout", "500");

        assertThatThrownBy(() -> DavConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("carddav.admin.user should not be empty");
    }
}