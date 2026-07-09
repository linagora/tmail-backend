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

package com.linagora.tmail.smtp;

import jakarta.inject.Singleton;

import org.apache.james.modules.protocols.SmtpDefaultSaslMechanismFactories;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.protocols.smtp.core.esmtp.LoginSaslMechanismFactory;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.linagora.tmail.sasl.TMailPlainSaslMechanismFactory;

public class TMailSMTPModule extends AbstractModule {
    @Provides
    @Singleton
    @SmtpDefaultSaslMechanismFactories
    ImmutableList<SaslMechanismFactory> provideDefaultSmtpSaslMechanismFactories(OauthBearerSaslMechanismFactory oauthBearer,
                                                                                 XOauth2SaslMechanismFactory xoauth2) {
        TMailPlainSaslMechanismFactory plain = new TMailPlainSaslMechanismFactory(false,
            PlainSaslMechanismFactory.IGNORE_REQUIRE_SSL_CONFIGURATION);
        return ImmutableList.of(new LoginSaslMechanismFactory(plain), plain, oauthBearer, xoauth2);
    }
}
