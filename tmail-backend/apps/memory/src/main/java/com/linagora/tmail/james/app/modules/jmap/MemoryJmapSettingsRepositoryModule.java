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

import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.user.api.UsernameChangeTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUserDeletionTaskStep;
import com.linagora.tmail.james.jmap.settings.JmapSettingsUsernameChangeTaskStep;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

public class MemoryJmapSettingsRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MemoryJmapSettingsRepository.class).in(Scopes.SINGLETON);
        bind(JmapSettingsRepository.class).to(MemoryJmapSettingsRepository.class)
            .in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding()
            .to(JmapSettingsUsernameChangeTaskStep.class);

        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class)
            .addBinding()
            .to(JmapSettingsUserDeletionTaskStep.class);
    }
}
