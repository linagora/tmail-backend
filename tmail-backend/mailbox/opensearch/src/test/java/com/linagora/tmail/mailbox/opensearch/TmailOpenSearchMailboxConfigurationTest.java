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

package com.linagora.tmail.mailbox.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.indices.IndexSettings;

class TmailOpenSearchMailboxConfigurationTest {
    @Test
    void indexCodecShouldDefaultToBestCompression() {
        assertThat(TmailOpenSearchMailboxConfiguration.fromProperties(new PropertiesConfiguration()).indexCodec())
            .isEqualTo("best_compression");
    }

    @Test
    void indexCodecShouldBeReadFromProperties() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("opensearch.mailbox.index.codec", "zstd");

        assertThat(TmailOpenSearchMailboxConfiguration.fromProperties(configuration).indexCodec())
            .isEqualTo("zstd");
    }

    @Test
    void mailboxIndexSettingsShouldCarryTheConfiguredCodec() {
        TmailMailboxMappingFactory mappingFactory = new TmailMailboxMappingFactory(OpenSearchConfiguration.DEFAULT_CONFIGURATION,
            TmailOpenSearchMailboxConfiguration.builder().indexCodec("zstd_no_dict").build());

        assertThat(mappingFactory.getIndexSettings())
            .map(IndexSettings::codec)
            .contains("zstd_no_dict");
    }
}
