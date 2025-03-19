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

package com.linagora.tmail.james.jmap.firebase;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.user.api.DeleteUserDataTaskStep;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class PostgresFirebaseRepositoryModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<PostgresDataDefinition> postgresDataDefinitions = Multibinder.newSetBinder(binder(), PostgresDataDefinition.class);
        postgresDataDefinitions.addBinding().toInstance(PostgresFirebaseModule.MODULE);

        bind(FirebaseSubscriptionRepository.class).to(PostgresFirebaseSubscriptionRepository.class);
        bind(PostgresFirebaseSubscriptionRepository.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), DeleteUserDataTaskStep.class).addBinding().to(FirebaseSubscriptionUserDeletionTaskStep.class);
    }
}