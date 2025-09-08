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

package com.linagora.tmail.rate.limiter.api.memory;

import org.junit.jupiter.api.BeforeEach;

import com.linagora.tmail.rate.limiter.api.RateLimitingRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingRepositoryContract;

public class MemoryRateLimitingRepositoryTest implements RateLimitingRepositoryContract {
    private MemoryRateLimitingRepository memoryRateLimitingRepository;

    @BeforeEach
    void setUp() {
        memoryRateLimitingRepository = new MemoryRateLimitingRepository();
    }

    @Override
    public RateLimitingRepository testee() {
        return memoryRateLimitingRepository;
    }
}
