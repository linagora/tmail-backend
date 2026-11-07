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

package com.linagora.tmail.webadmin.domainsignature;

import org.apache.james.modules.mailbox.ListenersConfiguration;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.james.webadmin.Routes;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.jmap.domainsignature.DomainSignatureTemplateApplyService;
import com.linagora.tmail.james.jmap.event.IdentityProvisionListener;
import com.linagora.tmail.james.jmap.event.LdapSignatureAttributes;

public class DomainSignatureTemplateRoutesModule extends AbstractModule {

    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), Routes.class)
            .addBinding()
            .to(DomainSignatureTemplateRoutes.class);
        bind(DomainSignatureTemplateApplyService.class);
    }

    // Applying a template reads the same LDAP attributes as the IdentityProvisionListener provisioning identities
    @Provides
    @Singleton
    LdapSignatureAttributes provideLdapSignatureAttributes(LdapRepositoryConfiguration ldapConfiguration,
                                                           ListenersConfiguration listenersConfiguration) {
        return listenersConfiguration.getListenersConfiguration()
            .stream()
            .filter(listener -> listener.getClazz().equals(IdentityProvisionListener.class.getCanonicalName()))
            .map(listener -> listener.getConfiguration()
                .map(configuration -> LdapSignatureAttributes.from(ldapConfiguration, configuration))
                .orElseGet(() -> LdapSignatureAttributes.from(ldapConfiguration)))
            .reduce(LdapSignatureAttributes::union)
            .orElseGet(() -> LdapSignatureAttributes.from(ldapConfiguration));
    }
}
