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

package com.linagora.tmail.blob.guice;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.modules.mailbox.CassandraCacheSessionModule;
import org.junit.jupiter.api.Test;

public class BlobStoreCacheModulesChooserTest {

    @Test
    void chooseModulesShouldReturnCacheDisabledModuleWhenCacheDisabled() {
        assertThat(BlobStoreCacheModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .disableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .hasSize(1)
            .first()
            .isInstanceOf(BlobStoreCacheModulesChooser.CacheDisabledModule.class);
    }

    @Test
    void chooseModulesShouldReturnCacheEnabledAndCassandraCacheModulesWhenCacheEnabled() {
        assertThat(BlobStoreCacheModulesChooser.chooseModules(BlobStoreConfiguration.builder()
            .s3()
            .noSecondaryS3BlobStore()
            .enableCache()
            .deduplication()
            .noCryptoConfig()
            .disableSingleSave()))
            .hasSize(2)
            .allSatisfy(module ->
                assertThat(module).isOfAnyClassIn(
                    BlobStoreCacheModulesChooser.CacheEnabledModule.class,
                    CassandraCacheSessionModule.class));
    }
}
