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

package com.linagora.calendar.app.modules;

import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.CanSendFrom;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.DelegationStore;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryDelegationStore;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;

public class MemoryUserModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(UsersRepository.class).to(MemoryUsersRepository.class);
        bind(DomainList.class).to(MemoryDomainList.class);

        // Needed in order to satisfy domain routes binding
        bind(MemoryRecipientRewriteTable.class).in(Scopes.SINGLETON);
        bind(RecipientRewriteTable.class).to(MemoryRecipientRewriteTable.class);
        // Needed in order to satisfy users routes binding
        bind(MemoryDelegationStore.class).in(Scopes.SINGLETON);
        bind(DelegationStore.class).to(MemoryDelegationStore.class);
        bind(AliasReverseResolverImpl.class).in(Scopes.SINGLETON);
        bind(AliasReverseResolver.class).to(AliasReverseResolverImpl.class);
        bind(CanSendFromImpl.class).in(Scopes.SINGLETON);
        bind(CanSendFrom.class).to(CanSendFromImpl.class);

        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(CalendarDataProbe.class);
    }

    @Provides
    @Singleton
    MemoryUsersRepository provideRepository(DomainList domainList) {
        return MemoryUsersRepository.withVirtualHosting(domainList);
    }

    @Provides
    @Singleton
    MemoryDomainList provideDomainList(DNSService service) {
        return new MemoryDomainList(service);
    }

    @ProvidesIntoSet
    InitializationOperation configureDomainList(MemoryDomainList memoryDomainList) {
        DomainListConfiguration domainListConfiguration = DomainListConfiguration.DEFAULT; // TODO load this from conf?
        return InitilizationOperationBuilder
            .forClass(MemoryDomainList.class)
            .init(() -> memoryDomainList.configure(domainListConfiguration));
    }
}
