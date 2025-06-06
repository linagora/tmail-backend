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

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.Disconnector;
import org.apache.james.events.EventListener;
import org.apache.james.jmap.method.Method;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.method.FirebaseCapabilitiesModule;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionGetMethod;
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetMethod;

public class FirebaseCommonModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new FirebaseCapabilitiesModule());

        Multibinder.newSetBinder(binder(), Method.class)
            .addBinding()
            .to(FirebaseSubscriptionGetMethod.class);

        Multibinder.newSetBinder(binder(), Method.class)
            .addBinding()
            .to(FirebaseSubscriptionSetMethod.class);

        bind(FirebasePushClient.class).in(Scopes.SINGLETON);

        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding().to(FirebasePushListener.class);

        Multibinder<Disconnector> disconnectorMultibinder = Multibinder.newSetBinder(binder(), Disconnector.class);
        disconnectorMultibinder.addBinding().to(FirebaseSubscriptionDisconnector.class);
    }

    @Provides
    @Singleton
    FirebaseConfiguration firebaseConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        Configuration configuration = propertiesProvider.getConfiguration("firebase");
        return FirebaseConfiguration.from(configuration);
    }
}
