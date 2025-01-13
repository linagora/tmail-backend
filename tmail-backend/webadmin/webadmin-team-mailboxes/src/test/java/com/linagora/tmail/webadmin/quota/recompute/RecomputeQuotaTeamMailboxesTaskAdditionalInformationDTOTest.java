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

import java.time.Instant;
import java.util.stream.Stream;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Domain;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;

public class RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTOTest {
    private static final TeamMailbox TEAM_MAILBOX1 = TeamMailbox.apply(Domain.of("abc.com"), TeamMailboxName.fromString("marketing").toOption().get());
    private static final TeamMailbox TEAM_MAILBOX2 = TeamMailbox.apply(Domain.of("xyz.com"), TeamMailboxName.fromString("sale").toOption().get());

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        ImmutableList<String> failedQuotaRoots = Stream.of(TEAM_MAILBOX1, TEAM_MAILBOX2)
            .map(teamMailbox -> teamMailbox.quotaRoot().asString())
            .collect(ImmutableList.toImmutableList());

        JsonSerializationVerifier.dtoModule(RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO.SERIALIZATION_MODULE)
            .bean(new RecomputeQuotaTeamMailboxesTask.Details(
                Instant.parse("2007-12-03T10:15:30.00Z"),
                Domain.of("linagora.com"),
                1,
                failedQuotaRoots))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recompute_quota_team_mailboxes.additionalInformation.json"))
            .verify();
    }
}
