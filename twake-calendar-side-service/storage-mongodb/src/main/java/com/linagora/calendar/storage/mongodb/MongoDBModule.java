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

package com.linagora.calendar.storage.mongodb;

import java.io.FileNotFoundException;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.calendar.storage.DomainConfiguration;
import com.linagora.calendar.storage.OpenPaaSDomainDAO;
import com.linagora.calendar.storage.OpenPaaSDomainList;
import com.linagora.calendar.storage.OpenPaaSUserDAO;
import com.mongodb.reactivestreams.client.MongoDatabase;

public class MongoDBModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MongoDBOpenPaaSUserDAO.class).in(Scopes.SINGLETON);
        bind(MongoDBOpenPaaSDomainDAO.class).in(Scopes.SINGLETON);

        bind(OpenPaaSDomainDAO.class).to(MongoDBOpenPaaSDomainDAO.class);
        bind(OpenPaaSUserDAO.class).to(MongoDBOpenPaaSUserDAO.class);

        bind(OpenPaaSDomainList.class).in(Scopes.SINGLETON);
        bind(DomainList.class).to(OpenPaaSDomainList.class);
    }

    @Provides
    @Singleton
    DomainConfiguration domainConfiguration(PropertiesProvider propertiesProvider) throws Exception {
        try {
            return DomainConfiguration.parseConfiguration(propertiesProvider.getConfiguration("configuration"));
        } catch (FileNotFoundException e) {
            return new DomainConfiguration(ImmutableList.of(Domain.of("linagora.com")));
        }
    }

    @Provides
    @Singleton
    MongoDBConfiguration mongoConfiguration(PropertiesProvider propertiesProvider) throws Exception {
        try {
            return MongoDBConfiguration.parse(propertiesProvider.getConfiguration("configuration"));
        } catch (FileNotFoundException e) {
            return new MongoDBConfiguration("dummy", "ignored");
        }
    }

    @ProvidesIntoSet
    InitializationOperation addDomains(DomainConfiguration domainConfiguration, OpenPaaSDomainList domainList) {
        return InitilizationOperationBuilder
            .forClass(OpenPaaSDomainList.class)
            .init(() -> domainConfiguration.getDomains().forEach(domainList::addDomainLenient));
    }

    @Provides
    @Singleton
    MongoDatabase dataBase(MongoDBConfiguration configuration) {
        return MongoDBConnectionFactory.instantiateDB(configuration);
    }
}
