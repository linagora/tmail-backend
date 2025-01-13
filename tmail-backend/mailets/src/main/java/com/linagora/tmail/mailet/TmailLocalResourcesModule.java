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

package com.linagora.tmail.mailet;

import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.mailetcontainer.impl.LocalResourcesImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.linagora.tmail.team.TeamMailboxRepository;

public class TmailLocalResourcesModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(LocalResources.class).to(TmailLocalResources.class);
    }

    @Provides
    @Singleton
    TmailLocalResources localResources(LocalResourcesImpl localResources, TeamMailboxRepository teamMailboxRepository) {
        return new TmailLocalResources(localResources, teamMailboxRepository);
    }
}
