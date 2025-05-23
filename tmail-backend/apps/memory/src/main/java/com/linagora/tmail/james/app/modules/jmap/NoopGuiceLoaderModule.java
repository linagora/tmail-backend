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

import org.apache.james.utils.GuiceLoader;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.linagora.tmail.james.app.NoopGuiceLoader;

public class NoopGuiceLoaderModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(GuiceLoader.class).to(NoopGuiceLoader.class).in(Singleton.class);
    }
}