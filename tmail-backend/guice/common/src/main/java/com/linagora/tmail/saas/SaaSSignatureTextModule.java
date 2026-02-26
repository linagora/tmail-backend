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

package com.linagora.tmail.saas;

import java.util.Optional;

import jakarta.inject.Named;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.modules.mailbox.ListenerConfiguration;
import org.apache.james.modules.mailbox.ListenersConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.linagora.tmail.james.jmap.event.IdentityProvisionListener;
import com.linagora.tmail.james.jmap.event.SignatureTextFactory;
import com.linagora.tmail.saas.listener.SaaSSignatureTextFactory;

public class SaaSSignatureTextModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SignatureTextFactory.class).to(SaaSSignatureTextFactory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    @Named(SaaSSignatureTextFactory.IDENTITY_PROVISION_LISTENER_CONFIGURATION)
    public HierarchicalConfiguration<ImmutableNode> provideIdentityProvisionListenerConfiguration(ListenersConfiguration listenersConfiguration) {
        return listenersConfiguration.getListenersConfiguration()
            .stream()
            .filter(listenerConfiguration -> listenerConfiguration.getClazz().equals(IdentityProvisionListener.class.getCanonicalName()))
            .map(ListenerConfiguration::getConfiguration)
            .flatMap(Optional::stream)
            .findFirst()
            .orElseGet(BaseHierarchicalConfiguration::new);
    }
}
