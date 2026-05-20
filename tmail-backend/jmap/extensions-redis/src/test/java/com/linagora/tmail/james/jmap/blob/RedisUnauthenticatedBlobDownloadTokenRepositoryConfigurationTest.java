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

package com.linagora.tmail.james.jmap.blob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class RedisUnauthenticatedBlobDownloadTokenRepositoryConfigurationTest {
    @Test
    void shouldDefaultCommandTimeout() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();

        assertThat(RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.from(configuration).commandTimeout())
            .isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldParseDuration() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.COMMAND_TIMEOUT_PROPERTY, "500ms");

        assertThat(RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.from(configuration).commandTimeout())
            .isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void shouldRejectInvalidCommandTimeout() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.COMMAND_TIMEOUT_PROPERTY, "invalid");

        assertThatThrownBy(() -> RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.from(configuration))
            .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldRejectZeroCommandTimeout() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty(RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.COMMAND_TIMEOUT_PROPERTY, "0seconds");

        assertThatThrownBy(() -> RedisUnauthenticatedBlobDownloadTokenRepositoryConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
