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

package com.linagora.tmail.team;

import org.apache.james.UserEntityValidator;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.rrt.api.CanSendFrom;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.mailet.TmailLocalResourcesModule;

public class TeamMailboxModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TeamMailboxRepositoryImpl.class).in(Scopes.SINGLETON);
        bind(TMailCanSendFrom.class).in(Scopes.SINGLETON);
        bind(TMailQuotaRootResolver.class).in(Scopes.SINGLETON);

        bind(TeamMailboxRepository.class).to(TeamMailboxRepositoryImpl.class);
        bind(CanSendFrom.class).to(TMailCanSendFrom.class);
        bind(QuotaRootResolver.class).to(TMailQuotaRootResolver.class);
        bind(UserQuotaRootResolver.class).to(TMailQuotaRootResolver.class);

        Multibinder.newSetBinder(binder(), UserEntityValidator.class)
            .addBinding()
            .to(TeamMailboxUserEntityValidator.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(PropagateDeleteRightTeamMailboxListener.class);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(TeamMailboxSubscriptionListener.class);

        install(new TmailLocalResourcesModule());
    }
}
