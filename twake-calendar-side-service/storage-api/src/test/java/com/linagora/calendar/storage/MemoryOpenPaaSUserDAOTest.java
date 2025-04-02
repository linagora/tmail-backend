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

package com.linagora.calendar.storage;

import org.junit.jupiter.api.BeforeEach;

public class MemoryOpenPaaSUserDAOTest implements OpenPaaSUserDAOContract {
    private MemoryOpenPaaSUserDAO testee;

    @BeforeEach
    void setUp() {
        testee = new MemoryOpenPaaSUserDAO();
    }

    @Override
    public OpenPaaSUserDAO testee() {
        return testee;
    }
}
