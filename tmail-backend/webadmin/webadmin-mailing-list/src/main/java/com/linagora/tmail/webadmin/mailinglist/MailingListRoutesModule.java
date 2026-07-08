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

package com.linagora.tmail.webadmin.mailinglist;

import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.webadmin.Routes;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.mailet.MailingListConfiguration;
import com.unboundid.ldap.sdk.LDAPConnectionPool;

/**
 * Wires the {@code /mailingLists} webadmin routes on top of LDAP. It relies on {@code LDAPConnectionPool} and
 * {@code LdapRepositoryConfiguration} being available and should therefore be loaded only for LDAP based deployments.
 * When no {@code baseDN} is configured (typically because {@code mailingLists.properties} is missing), an
 * {@link UnconfiguredMailingListRepository} is bound so the routes answer with a {@code 409 Conflict}.
 */
public class MailingListRoutesModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(MailingListRoutes.class);
    }

    @Provides
    @Singleton
    MailingListRepository provideMailingListRepository(LDAPConnectionPool ldapConnectionPool,
                                                       LdapRepositoryConfiguration ldapConfiguration,
                                                       MailingListConfiguration mailingListConfiguration) {
        if (mailingListConfiguration.baseDN().isEmpty()) {
            return new UnconfiguredMailingListRepository();
        }
        return new LdapMailingListRepository(ldapConnectionPool, ldapConfiguration, mailingListConfiguration);
    }
}
