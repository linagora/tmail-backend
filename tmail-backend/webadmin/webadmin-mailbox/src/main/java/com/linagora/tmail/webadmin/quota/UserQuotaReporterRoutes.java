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

package com.linagora.tmail.webadmin.quota;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;
import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.webadmin.quota.dto.ExtraQuotaSumDTO;
import com.linagora.tmail.webadmin.quota.dto.UserSpecificQuotaDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spark.Request;
import spark.Service;

public class UserQuotaReporterRoutes implements Routes {
    private static final String USERS_QUOTA_BASE_PATH = "/quota/users";
    private static final String LIST_USERS_WITH_SPECIFIC_QUOTA_ENDPOINT = USERS_QUOTA_BASE_PATH;
    private static final String COUNT_USERS_WITH_SPECIFIC_QUOTA_ENDPOINT = USERS_QUOTA_BASE_PATH + "/count";
    private static final String SUM_USERS_EXTRA_QUOTA_ENDPOINT = USERS_QUOTA_BASE_PATH + "/sum";

    private final UserQuotaReporter userQuotaReporter;
    private final JsonTransformer jsonTransformer;

    @Inject
    public UserQuotaReporterRoutes(UserQuotaReporter userQuotaReporter, JsonTransformer jsonTransformer) {
        this.userQuotaReporter = userQuotaReporter;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public String getBasePath() {
        return USERS_QUOTA_BASE_PATH;
    }

    @Override
    public void define(Service service) {
        defineListUsersHavingSpecificQuota(service);
        defineCountUsersHavingSpecificQuota(service);
        defineSumExtraQuota(service);
    }

    private void defineListUsersHavingSpecificQuota(Service service) {
        service.get(LIST_USERS_WITH_SPECIFIC_QUOTA_ENDPOINT,
            (request, response) -> {
                hasSpecificQuotaParamPreconditions(request);
                return Flux.from(userQuotaReporter.usersWithSpecificQuota())
                    .map(UserSpecificQuotaDTO::from)
                    .collectList()
                    .block();
            },
            jsonTransformer);
    }

    private void defineCountUsersHavingSpecificQuota(Service service) {
        service.get(COUNT_USERS_WITH_SPECIFIC_QUOTA_ENDPOINT,
            (request, response) -> {
                hasSpecificQuotaParamPreconditions(request);
                return Mono.from(userQuotaReporter.usersWithSpecificQuotaCount()).block();
            },
            jsonTransformer);
    }

    private void defineSumExtraQuota(Service service) {
        service.get(SUM_USERS_EXTRA_QUOTA_ENDPOINT,
            (request, response) -> {
                hasSpecificQuotaParamPreconditions(request);
                return Mono.from(userQuotaReporter.usersExtraQuotaSum())
                    .map(ExtraQuotaSumDTO::from)
                    .block();
            },
            jsonTransformer);
    }

    private void hasSpecificQuotaParamPreconditions(Request request) {
        Preconditions.checkArgument(Optional.ofNullable(request.queryParams("hasSpecificQuota"))
                .isPresent(),
            "'hasSpecificQuota' query parameter is missing");
    }
}

