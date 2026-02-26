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

package com.linagora.tmail.james.app;

import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.saas.SaaSCapabilitiesModule;
import com.linagora.tmail.saas.SaaSSignatureTextModule;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.SaaSAccountUsernameChangeTaskStep;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;

public class MemorySaaSModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new SaaSCapabilitiesModule());
        install(new SaaSSignatureTextModule());

        bind(SaaSAccountRepository.class).to(MemorySaaSAccountRepository.class)
            .in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding()
            .to(SaaSAccountUsernameChangeTaskStep.class);
    }
}
