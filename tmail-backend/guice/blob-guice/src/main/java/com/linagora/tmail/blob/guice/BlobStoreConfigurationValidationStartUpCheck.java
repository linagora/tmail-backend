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

import jakarta.inject.Inject;

import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.modules.blobstore.validation.EventsourcingStorageStrategy;

import com.google.common.annotations.VisibleForTesting;

public class BlobStoreConfigurationValidationStartUpCheck implements StartUpCheck {

    private static final String BLOB_STORE_CONFIGURATION_VALIDATION = "blobStore-configuration-validation";
    private final BlobStoreConfiguration blobStoreConfiguration;
    private final EventsourcingStorageStrategy eventsourcingStorageStrategy;

    @VisibleForTesting
    @Inject
    BlobStoreConfigurationValidationStartUpCheck(BlobStoreConfiguration blobStoreConfiguration, EventsourcingStorageStrategy eventsourcingStorageStrategy) {
        this.blobStoreConfiguration = blobStoreConfiguration;
        this.eventsourcingStorageStrategy = eventsourcingStorageStrategy;
    }

    @Override
    public CheckResult check() {
        try {
            eventsourcingStorageStrategy.registerStorageStrategy(blobStoreConfiguration.storageStrategy());
            return CheckResult.builder()
                .checkName(BLOB_STORE_CONFIGURATION_VALIDATION)
                .resultType(ResultType.GOOD)
                .build();
        } catch (IllegalStateException e) {
            return CheckResult.builder()
                .checkName(BLOB_STORE_CONFIGURATION_VALIDATION)
                .resultType(ResultType.BAD)
                .description(e.getMessage())
                .build();
        }
    }

    @Override
    public String checkName() {
        return BLOB_STORE_CONFIGURATION_VALIDATION;
    }
}
