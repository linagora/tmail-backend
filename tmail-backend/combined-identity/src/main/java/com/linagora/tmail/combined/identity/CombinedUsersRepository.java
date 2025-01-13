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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.UsersRepositoryImpl;

public class CombinedUsersRepository extends UsersRepositoryImpl<CombinedUserDAO> {

    @Inject
    public CombinedUsersRepository(DomainList domainList,
                                   CombinedUserDAO usersDAO) {
        super(domainList, usersDAO);
    }

    @Override
    public Optional<Username> test(Username name, String password) throws UsersRepositoryException {
        return usersDAO.test(name, password);
    }
}
