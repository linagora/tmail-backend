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

package com.linagora.tmail;

import java.util.Optional;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.api.OpenPaasServerExtension;
import com.linagora.tmail.configuration.DavConfiguration;
import com.linagora.tmail.configuration.OpenPaasConfiguration;

public class OpenPaasTestModule extends AbstractModule {

    private final OpenPaasServerExtension openPaasServerExtension;
    private final Optional<DavConfiguration> davConfiguration;
    private final Optional<OpenPaasConfiguration.ContactConsumerConfiguration> contactConsumerConfiguration;

    public OpenPaasTestModule(OpenPaasServerExtension openPaasServerExtension,
                              Optional<DavConfiguration> davConfiguration,
                              Optional<OpenPaasConfiguration.ContactConsumerConfiguration> contactConsumerConfiguration) {
        this.openPaasServerExtension = openPaasServerExtension;
        this.davConfiguration = davConfiguration;
        this.contactConsumerConfiguration = contactConsumerConfiguration;
    }

    @Provides
    @Singleton
    public OpenPaasConfiguration provideOpenPaasServerExtension() {
        return new OpenPaasConfiguration(openPaasServerExtension.getBaseUrl(),
            openPaasServerExtension.getUsername(),
            openPaasServerExtension.getPassword(),
            false,
            contactConsumerConfiguration,
            davConfiguration);
    }
}
