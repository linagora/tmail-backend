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

package com.linagora.tmail.webadmin;

import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DTOModuleInjections;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesRoutes;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesService;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesTaskDTO;

public class TeamMailboxRoutesModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(TeamMailboxManagementRoutes.class);
        routesMultibinder.addBinding().to(UserTeamMailboxRoutes.class);
        routesMultibinder.addBinding().to(TeamMailboxQuotaRoutes.class);
        routesMultibinder.addBinding().to(RecomputeQuotaTeamMailboxesRoutes.class);
    }

    @ProvidesIntoSet
    public TaskDTOModule<? extends Task, ? extends TaskDTO> recomputeQuotaTeamMailboxesTask(RecomputeQuotaTeamMailboxesService recomputeQuotaTeamMailboxesService) {
        return RecomputeQuotaTeamMailboxesTaskDTO.module(recomputeQuotaTeamMailboxesService);
    }

    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> recomputeQuotaTeamMailboxesAdditionalInformation() {
        return RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }

    @Named(DTOModuleInjections.WEBADMIN_DTO)
    @ProvidesIntoSet
    public AdditionalInformationDTOModule<? extends TaskExecutionDetails.AdditionalInformation, ? extends AdditionalInformationDTO> webAdminRecomputeQuotaTeamMailboxesAdditionalInformation() {
        return RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO.SERIALIZATION_MODULE;
    }
}
