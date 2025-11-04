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
 *******************************************************************/

package com.linagora.tmail.mailbox.quota.memory;

import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.UserQuotaReporterContract;

public class MemoryUserQuotaReporterTest implements UserQuotaReporterContract {
    private InMemoryPerUserMaxQuotaManager memoryMaxQuotaManager;
    private MemoryUserQuotaReporter testee;

    @BeforeEach
    void setUp() {
        memoryMaxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        testee = new MemoryUserQuotaReporter(memoryMaxQuotaManager);
    }

    @Override
    public MaxQuotaManager maxQuotaManager() {
        return memoryMaxQuotaManager;
    }

    @Override
    public UserQuotaReporter testee() {
        return testee;
    }
}
