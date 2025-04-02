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

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class MemoryOpenPaaSDAOModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(MemoryOpenPaaSUserDAO.class).in(Scopes.SINGLETON);
        bind(MemoryOpenPaaSDomainDAO.class).in(Scopes.SINGLETON);

        bind(OpenPaaSDomainDAO.class).to(MemoryOpenPaaSDomainDAO.class);
        bind(OpenPaaSUserDAO.class).to(MemoryOpenPaaSUserDAO.class);
    }
}
