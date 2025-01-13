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

package com.linagora.tmail.rspamd;

import java.io.FileNotFoundException;
import java.time.Clock;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.rspamd.client.RspamdClientConfiguration;
import org.apache.james.rspamd.client.RspamdHttpClient;
import org.apache.james.rspamd.healthcheck.RspamdHealthCheck;
import org.apache.james.rspamd.route.FeedMessageRoute;
import org.apache.james.rspamd.task.FeedHamToRspamdTaskAdditionalInformationDTO;
import org.apache.james.rspamd.task.FeedHamToRspamdTaskDTO;
import org.apache.james.rspamd.task.FeedSpamToRspamdTaskAdditionalInformationDTO;
import org.apache.james.rspamd.task.FeedSpamToRspamdTaskDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.PropertiesProvider;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DTOModuleInjections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

public class RspamdModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RspamdModule.class);

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(FeedMessageRoute.class);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(RspamdHealthCheck.class);
    }

    @Provides
    @Singleton
    public RspamdClientConfiguration rspamdClientConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        return RspamdClientConfiguration.from(propertiesProvider.getConfiguration("rspamd"));
    }

    @Provides
    @Singleton
    public RspamdHttpClient rspamdHttpClient(RspamdClientConfiguration rspamdClientConfiguration) {
        return new RspamdHttpClient(rspamdClientConfiguration);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> feedSpamToRspamdTaskDTOModule(MailboxManager mailboxManager,
                                                                                          UsersRepository usersRepository,
                                                                                          MessageIdManager messageIdManager,
                                                                                          MailboxSessionMapperFactory mapperFactory,
                                                                                          RspamdHttpClient rspamdHttpClient,
                                                                                          Clock clock,
                                                                                          RspamdClientConfiguration rspamdConfiguration) {
        return FeedSpamToRspamdTaskDTO.module(mailboxManager, usersRepository, messageIdManager, mapperFactory, rspamdHttpClient, clock, rspamdConfiguration);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> feedHamToRspamdTaskDTOModule(MailboxManager mailboxManager,
                                                                                         UsersRepository usersRepository,
                                                                                         MessageIdManager messageIdManager,
                                                                                         MailboxSessionMapperFactory mapperFactory,
                                                                                         RspamdHttpClient rspamdHttpClient,
                                                                                         Clock clock,
                                                                                         RspamdClientConfiguration rspamdConfiguration) {
        return FeedHamToRspamdTaskDTO.module(mailboxManager, usersRepository, messageIdManager, mapperFactory, rspamdHttpClient, clock, rspamdConfiguration);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> feedSpamToRspamdTaskAdditionalInformationDTOModule() {
        return FeedSpamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> feedSpamToRspamdTaskAdditionalInformationDTOWebadminModule() {
        return FeedSpamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> feedHamToRspamdTaskAdditionalInformationDTOModule() {
        return FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> feedHamToRspamdTaskAdditionalInformationDTOWebadminModule() {
        return FeedHamToRspamdTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }
}
