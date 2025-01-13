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

package com.linagora.tmail.webadmin.quota.recompute;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Domain;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecomputeQuotaTeamMailboxesTaskSerializationTest {
    RecomputeQuotaTeamMailboxesService recomputeQuotaTeamMailboxesService;

    @BeforeEach
    void setUp() {
        recomputeQuotaTeamMailboxesService = mock(RecomputeQuotaTeamMailboxesService.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeQuotaTeamMailboxesTaskDTO.module(recomputeQuotaTeamMailboxesService))
            .bean(new RecomputeQuotaTeamMailboxesTask(
                recomputeQuotaTeamMailboxesService,
                Domain.of("linagora.com")))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recompute_quota_team_mailboxes.task.json"))
            .verify();
    }
}
