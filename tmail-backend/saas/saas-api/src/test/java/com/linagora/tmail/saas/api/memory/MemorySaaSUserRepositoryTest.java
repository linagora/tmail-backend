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

package com.linagora.tmail.saas.api.memory;

import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.saas.api.SaaSUserRepository;
import com.linagora.tmail.saas.api.SaaSUserRepositoryContract;

public class MemorySaaSUserRepositoryTest implements SaaSUserRepositoryContract {
    private MemorySaaSUserRepository memorySaaSUserRepository;

    @BeforeEach
    void setUp() {
        memorySaaSUserRepository = new MemorySaaSUserRepository();
    }

    @Override
    public SaaSUserRepository testee() {
        return memorySaaSUserRepository;
    }
}
