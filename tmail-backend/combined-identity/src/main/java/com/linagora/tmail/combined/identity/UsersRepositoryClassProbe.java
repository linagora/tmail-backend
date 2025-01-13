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

package com.linagora.tmail.combined.identity;

import jakarta.inject.Inject;

import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.lib.UsersDAO;
import org.apache.james.utils.GuiceProbe;

public class UsersRepositoryClassProbe implements GuiceProbe {
    private final UsersRepository usersRepository;
    private final UsersDAO usersDAO;

    @Inject
    public UsersRepositoryClassProbe(UsersRepository usersRepository, UsersDAO usersDAO) {
        this.usersRepository = usersRepository;
        this.usersDAO = usersDAO;
    }

    public Class<? extends UsersRepository> getUserRepositoryClass() {
        return usersRepository.getClass();
    }

    public Class<? extends UsersDAO> getUsersDAOClass() {
        return usersDAO.getClass();
    }
}
