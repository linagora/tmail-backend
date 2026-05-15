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

package com.linagora.tmail.james.app.modules.jmap;

import java.time.Clock;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.UnauthenticatedBlobAccessConfiguration;
import com.linagora.tmail.james.jmap.blob.MemoryUnauthenticatedBlobDownloadTokenRepository;
import com.linagora.tmail.james.jmap.blob.UnauthenticatedBlobDownloadTokenRepository;

public class MemoryUnauthenticatedDownloadTokenRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(UnauthenticatedBlobDownloadTokenRepository.class).to(MemoryUnauthenticatedBlobDownloadTokenRepository.class);
    }

    @Provides
    @Singleton
    MemoryUnauthenticatedBlobDownloadTokenRepository provideMemoryUnauthenticatedDownloadTokenRepository(Clock clock, UnauthenticatedBlobAccessConfiguration configuration) {
        return new MemoryUnauthenticatedBlobDownloadTokenRepository(clock, configuration.tokenTtl());
    }
}
