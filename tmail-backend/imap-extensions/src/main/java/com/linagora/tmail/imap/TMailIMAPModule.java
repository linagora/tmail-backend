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

package com.linagora.tmail.imap;

import jakarta.inject.Singleton;

import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.NamespaceSupplier;
import org.apache.james.modules.protocols.ImapDefaultSaslMechanismFactories;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

public class TMailIMAPModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(NamespaceSupplier.class).to(TMailNamespaceSupplier.class).in(Scopes.SINGLETON);
        bind(PathConverter.Factory.class).to(TMailPathConverterFactory.class).in(Scopes.SINGLETON);
    }

    /**
     * TMailPlainSaslMechanismFactory falls back to James PLAIN unless the current imapserver configuration uses a TMail IMAP package.
     */
    @Provides
    @Singleton
    @ImapDefaultSaslMechanismFactories
    ImmutableList<SaslMechanismFactory> provideDefaultImapSaslMechanismFactories(TMailPlainSaslMechanismFactory plain,
                                                                                 OauthBearerSaslMechanismFactory oauthBearer,
                                                                                 XOauth2SaslMechanismFactory xoauth2) {
        return ImmutableList.of(plain, oauthBearer, xoauth2);
    }
}
