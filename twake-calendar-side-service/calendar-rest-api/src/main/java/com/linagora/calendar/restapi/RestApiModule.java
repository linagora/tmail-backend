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

package com.linagora.calendar.restapi;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Set;

import jakarta.inject.Named;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.jmap.JMAPRoutes;
import org.apache.james.jmap.http.AuthenticationStrategy;
import org.apache.james.jmap.http.Authenticator;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.calendar.restapi.api.ConfigurationEntryResolver;
import com.linagora.calendar.restapi.auth.BasicAuthenticationStrategy;
import com.linagora.calendar.restapi.auth.JwtAuthenticationStrategy;
import com.linagora.calendar.restapi.auth.OidcAuthenticationStrategy;
import com.linagora.calendar.restapi.routes.AvatarRoute;
import com.linagora.calendar.restapi.routes.ConfigurationRoute;
import com.linagora.calendar.restapi.routes.JwtRoutes;
import com.linagora.calendar.restapi.routes.JwtSigner;
import com.linagora.calendar.restapi.routes.LogoRoute;
import com.linagora.calendar.restapi.routes.PeopleSearchRoute;
import com.linagora.calendar.restapi.routes.ProfileAvatarRoute;
import com.linagora.calendar.restapi.routes.ThemeRoute;
import com.linagora.calendar.restapi.routes.configuration.ConstantConfigurationEntryResolver;
import com.linagora.calendar.restapi.routes.configuration.FileConfigurationEntryResolver;
import com.linagora.calendar.restapi.routes.configuration.MongoConfigurationEntryResolver;

public class RestApiModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), GuiceProbe.class).addBinding().to(RestApiServerProbe.class);
        bind(CalendarRestApiServer.class).in(Scopes.SINGLETON);

        Multibinder<JMAPRoutes> routes = Multibinder.newSetBinder(binder(), JMAPRoutes.class);
        routes.addBinding().to(AvatarRoute.class);
        routes.addBinding().to(ThemeRoute.class);
        routes.addBinding().to(LogoRoute.class);
        routes.addBinding().to(JwtRoutes.class);
        routes.addBinding().to(ConfigurationRoute.class);
        routes.addBinding().to(PeopleSearchRoute.class);
        routes.addBinding().to(ProfileAvatarRoute.class);

        Multibinder<AuthenticationStrategy> authenticationStrategies = Multibinder.newSetBinder(binder(), AuthenticationStrategy.class);
        authenticationStrategies.addBinding().to(BasicAuthenticationStrategy.class);
        authenticationStrategies.addBinding().to(JwtAuthenticationStrategy.class);
        authenticationStrategies.addBinding().to(OidcAuthenticationStrategy.class);

        Multibinder<ConfigurationEntryResolver> configurationEntryResolvers = Multibinder.newSetBinder(binder(), ConfigurationEntryResolver.class);
        configurationEntryResolvers.addBinding().to(FileConfigurationEntryResolver.class);
        configurationEntryResolvers.addBinding().to(ConstantConfigurationEntryResolver.class);
        configurationEntryResolvers.addBinding().to(MongoConfigurationEntryResolver.class);
    }

    @Provides
    @Singleton
    Authenticator provideAuth(MetricFactory metricFactory, Set<AuthenticationStrategy> authenticationStrategies) {
        return Authenticator.of(metricFactory, authenticationStrategies);
    }

    @Provides
    @Singleton
    RestApiConfiguration provideConf(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return RestApiConfiguration.parseConfiguration(propertiesProvider);
        } catch (FileNotFoundException e) {
            return RestApiConfiguration.builder().build();
        }
    }

    @ProvidesIntoSet
    InitializationOperation startJmap(CalendarRestApiServer server) {
        return InitilizationOperationBuilder
            .forClass(CalendarRestApiServer.class)
            .init(server::start);
    }

    @Provides
    @Named("userInfo")
    URL provideIntrospectionEndpoint(RestApiConfiguration configuration) {
        return configuration.getOidcIntrospectionUrl();
    }

    @Provides
    @Singleton
    JwtSigner signer(JwtSigner.Factory factory) throws Exception {
        return factory.instancaiate();
    }
}
