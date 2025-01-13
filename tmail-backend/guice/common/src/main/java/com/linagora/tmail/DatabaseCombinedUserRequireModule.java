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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail;

import org.apache.james.user.lib.UsersDAO;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.linagora.tmail.combined.identity.CombinedUserDAO;

public class DatabaseCombinedUserRequireModule<T extends UsersDAO> extends AbstractModule {
    public static DatabaseCombinedUserRequireModule<?> of(Class<? extends UsersDAO> typeUserDAOClass) {
        return new DatabaseCombinedUserRequireModule<>(typeUserDAOClass);
    }

    final Class<T> typeUserDAOClass;

    public DatabaseCombinedUserRequireModule(Class<T> typeUserDAOClass) {
        this.typeUserDAOClass = typeUserDAOClass;
    }

    @Provides
    @Singleton
    @Named(CombinedUserDAO.DATABASE_INJECT_NAME)
    public UsersDAO provideDatabaseCombinedUserDAO(Injector injector) {
        return injector.getInstance(typeUserDAOClass);
    }

}
