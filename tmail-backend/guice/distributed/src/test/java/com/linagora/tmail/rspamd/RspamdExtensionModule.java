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

package com.linagora.tmail.rspamd;

import java.net.URL;
import java.util.Optional;

import org.apache.james.rspamd.RspamdExtension;
import org.apache.james.rspamd.client.RspamdClientConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class RspamdExtensionModule extends RspamdExtension {

    public static class TestRspamdModule extends AbstractModule {

        private final URL rspamdUrl;

        public TestRspamdModule(URL rspamdUrl) {
            this.rspamdUrl = rspamdUrl;
        }

        @Provides
        @Singleton
        public RspamdClientConfiguration rspamdClientConfiguration() {
            return new RspamdClientConfiguration(rspamdUrl, RspamdExtension.PASSWORD, Optional.empty());
        }
    }

    @Override
    public Module getModule() {
        return new TestRspamdModule(rspamdURL());
    }
}
