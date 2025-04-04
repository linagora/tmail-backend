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

package com.linagora.calendar.app;

import org.apache.james.ExtraProperties;
import org.apache.james.UserEntityValidator;
import org.apache.james.data.LdapUsersRepositoryModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.modules.mailbox.OpenSearchMailboxConfigurationModule;
import org.apache.james.modules.server.DNSServiceModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.protocols.lib.netty.CertificateReloadable;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.user.api.DeleteUserDataTaskStep;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.UserCondition;
import org.apache.james.webadmin.dropwizard.MetricsRoutes;
import org.apache.james.webadmin.routes.DomainsRoutes;
import org.apache.james.webadmin.routes.UserRoutes;

import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.linagora.calendar.app.modules.MemoryAutoCompleteModule;
import com.linagora.calendar.app.modules.MemoryUserModule;
import com.linagora.calendar.app.modules.OpenSearchClientModule;
import com.linagora.calendar.restapi.RestApiModule;
import com.linagora.calendar.storage.MemoryOpenPaaSDAOModule;
import com.linagora.calendar.storage.mongodb.MongoDBModule;
import com.linagora.tmail.james.jmap.module.OSContactAutoCompleteModule;

public class TwakeCalendarMain {
    public static final Module WEBADMIN = Modules.combine(
        new WebAdminServerModule(),
        new NoJwtModule(),
        binder -> Multibinder.newSetBinder(binder, Routes.class).addBinding().to(MetricsRoutes.class),
        binder -> Multibinder.newSetBinder(binder, Routes.class).addBinding().to(DomainsRoutes.class),
        binder -> Multibinder.newSetBinder(binder, Routes.class).addBinding().to(UserRoutes.class),
        binder -> Multibinder.newSetBinder(binder, DeleteUserDataTaskStep.class),
        binder -> MapBinder.newMapBinder(binder, String.class, UserCondition.class),
        binder -> binder.bind(UserEntityValidator.class).toInstance(UserEntityValidator.NOOP),
        binder -> Multibinder.newSetBinder(binder, CertificateReloadable.Factory.class),
        binder -> Multibinder.newSetBinder(binder, new TypeLiteral<TaskDTOModule<? extends Task, ? extends TaskDTO>>() {}),
        binder -> Multibinder.newSetBinder(binder, new TypeLiteral<DTOConverter<TaskExecutionDetails.AdditionalInformation, AdditionalInformationDTO>>() {}),
        binder -> Multibinder.newSetBinder(binder, new TypeLiteral<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>>() {}),
        binder -> Multibinder.newSetBinder(binder, new TypeLiteral<AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO>>() {}, Names.named("webadmin-dto")));

    public static void main(String[] args) throws Exception {
        TwakeCalendarConfiguration build = TwakeCalendarConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();
        TwakeCalendarGuiceServer server = createServer(build);
        main(server);
    }

    static void main(TwakeCalendarGuiceServer server) throws Exception {
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    public static TwakeCalendarGuiceServer createServer(TwakeCalendarConfiguration configuration) {
        ExtraProperties.initialize();

        return TwakeCalendarGuiceServer.forConfiguration(configuration)
            .combineWith(Modules.combine(
                new DNSServiceModule(),
                chooseDb(configuration.dbChoice()),
                chooseAutoComplete(configuration.autoCompleteChoice()),
                chooseUsersModule(configuration.userChoice()),
                new RestApiModule(),
                new TaskManagerModule(),
                WEBADMIN));
    }

    private static Module chooseAutoComplete(TwakeCalendarConfiguration.AutoCompleteChoice autoCompleteChoice) {
        return switch (autoCompleteChoice) {
            case MEMORY -> new MemoryAutoCompleteModule();
            case OPENSEARCH -> Modules.override(new MemoryAutoCompleteModule())
                .with(new OpenSearchClientModule(), new OSContactAutoCompleteModule(),
                    new OpenSearchMailboxConfigurationModule());
        };
    }

    private static Module chooseDb(TwakeCalendarConfiguration.DbChoice autoCompleteChoice) {
        return switch (autoCompleteChoice) {
            case MEMORY -> new MemoryOpenPaaSDAOModule();
            case MONGODB -> new MongoDBModule();
        };
    }

    public static Module chooseUsersModule(TwakeCalendarConfiguration.UserChoice userChoice) {
        return switch (userChoice) {
            case MEMORY -> new MemoryUserModule();
            case LDAP -> Modules.override(new MemoryUserModule()).with(new LdapUsersRepositoryModule());
        };
    }
}
