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

package com.linagora.calendar.app.modules;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.utils.GuiceProbe;

import com.linagora.calendar.storage.OpenPaaSId;
import com.linagora.calendar.storage.OpenPaaSUserDAO;

public class CalendarDataProbe implements GuiceProbe {
    private final UsersRepository usersRepository;
    private final DomainList domainList;
    private final OpenPaaSUserDAO usersDAO;

    @Inject
    public CalendarDataProbe(UsersRepository usersRepository, DomainList domainList, OpenPaaSUserDAO usersDAO) {
        this.usersRepository = usersRepository;
        this.domainList = domainList;
        this.usersDAO = usersDAO;
    }

    public CalendarDataProbe addDomain(Domain domain) {
        try {
            domainList.addDomain(domain);
            return this;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OpenPaaSId addUser(Username username, String password) {
        try {
            usersRepository.addUser(username, password);
            return usersDAO.add(username).block().id();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
