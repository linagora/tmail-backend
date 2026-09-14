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

package com.linagora.tmail.blob.bucket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

class BucketsStartUpCheckConfigurationTest {
    @Test
    void shouldBeEnabledByDefault() {
        assertThat(BucketsStartUpCheckConfiguration.from(new PropertiesConfiguration()))
            .isEqualTo(BucketsStartUpCheckConfiguration.ENABLED);
    }

    @Test
    void shouldBeEnabledWhenTrue() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("buckets.startup.check", "true");

        assertThat(BucketsStartUpCheckConfiguration.from(configuration))
            .isEqualTo(BucketsStartUpCheckConfiguration.ENABLED);
    }

    @Test
    void shouldBeDisabledWhenFalse() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("buckets.startup.check", "false");

        assertThat(BucketsStartUpCheckConfiguration.from(configuration))
            .isEqualTo(BucketsStartUpCheckConfiguration.DISABLED);
    }

    @Test
    void shouldRejectInvalidValues() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("buckets.startup.check", "invalid");

        assertThatThrownBy(() -> BucketsStartUpCheckConfiguration.from(configuration))
            .isInstanceOf(RuntimeException.class);
    }
}
