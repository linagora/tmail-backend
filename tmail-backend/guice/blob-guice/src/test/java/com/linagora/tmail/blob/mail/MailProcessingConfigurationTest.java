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

package com.linagora.tmail.blob.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.blob.api.BucketName;
import org.junit.jupiter.api.Test;

class MailProcessingConfigurationTest {
    private static MailProcessingConfiguration parse(String... lines) {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        for (String line : lines) {
            int separator = line.indexOf('=');
            configuration.addProperty(line.substring(0, separator), line.substring(separator + 1));
        }
        return MailProcessingConfiguration.from(configuration);
    }

    @Test
    void shouldDeduplicateByDefault() {
        assertThat(parse()).isEqualTo(MailProcessingConfiguration.DEDUPLICATED);
    }

    @Test
    void shouldDeduplicateWhenExplicitlyEnabled() {
        assertThat(parse("mailprocessing.deduplication.enabled=true"))
            .isEqualTo(MailProcessingConfiguration.DEDUPLICATED);
    }

    @Test
    void shouldNotDeduplicateWhenDisabled() {
        assertThat(parse("mailprocessing.deduplication.enabled=false"))
            .isEqualTo(MailProcessingConfiguration.duplicated());
    }

    @Test
    void shouldSupportTheEnableSpelling() {
        assertThat(parse("mailprocessing.deduplication.enable=false"))
            .isEqualTo(MailProcessingConfiguration.duplicated());
    }

    @Test
    void shouldSupportTheEnableSpellingWhenEnabled() {
        assertThat(parse("mailprocessing.deduplication.enable=true"))
            .isEqualTo(MailProcessingConfiguration.DEDUPLICATED);
    }

    @Test
    void enabledShouldTakePrecedenceOverItsAlias() {
        assertThat(parse("mailprocessing.deduplication.enabled=false", "mailprocessing.deduplication.enable=true"))
            .isEqualTo(MailProcessingConfiguration.duplicated());
    }

    @Test
    void shouldSupportBucketOverriding() {
        assertThat(parse("mailprocessing.deduplication.enabled=false", "mailprocessing.bucket=in-transit"))
            .isEqualTo(MailProcessingConfiguration.duplicated(BucketName.of("in-transit")));
    }

    @Test
    void bucketOverridingShouldBeIgnoredWhenDeduplicating() {
        assertThat(parse("mailprocessing.bucket=in-transit"))
            .isEqualTo(MailProcessingConfiguration.DEDUPLICATED);
    }
}
