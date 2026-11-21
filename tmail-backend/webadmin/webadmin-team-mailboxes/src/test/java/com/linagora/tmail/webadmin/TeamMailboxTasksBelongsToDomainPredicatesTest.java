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

import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_DOMAIN;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_DOMAIN_2;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.task.Hostname;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesTask;
import com.linagora.tmail.webadmin.vault.TeamMailboxVaultRestoreTask;

class TeamMailboxTasksBelongsToDomainPredicatesTest {
    private static TaskExecutionDetails details(Task task) {
        return TaskExecutionDetails.from(task, TaskId.generateTaskId(), new Hostname("localhost"));
    }

    @Test
    void vaultRestoreTaskShouldBelongToTheDomainOfTheTeamMailbox() {
        TaskExecutionDetails details = details(new TeamMailboxVaultRestoreTask(null, TEAM_MAILBOX));

        assertThat(TeamMailboxTasksBelongsToDomainPredicates.VAULT_RESTORE.belongsToDomain(TEAM_MAILBOX_DOMAIN, details)).isTrue();
        assertThat(TeamMailboxTasksBelongsToDomainPredicates.VAULT_RESTORE.belongsToDomain(TEAM_MAILBOX_DOMAIN_2, details)).isFalse();
    }

    @Test
    void recomputeQuotaTaskShouldBelongToItsDomain() {
        TaskExecutionDetails details = details(new RecomputeQuotaTeamMailboxesTask(null, TEAM_MAILBOX_DOMAIN));

        assertThat(TeamMailboxTasksBelongsToDomainPredicates.RECOMPUTE_QUOTA.belongsToDomain(TEAM_MAILBOX_DOMAIN, details)).isTrue();
        assertThat(TeamMailboxTasksBelongsToDomainPredicates.RECOMPUTE_QUOTA.belongsToDomain(TEAM_MAILBOX_DOMAIN_2, details)).isFalse();
    }

    @Test
    void predicatesShouldIgnoreOtherTasks() {
        TaskExecutionDetails details = details(new RecomputeQuotaTeamMailboxesTask(null, TEAM_MAILBOX_DOMAIN));

        assertThat(TeamMailboxTasksBelongsToDomainPredicates.VAULT_RESTORE.belongsToDomain(TEAM_MAILBOX_DOMAIN, details)).isFalse();
    }
}
