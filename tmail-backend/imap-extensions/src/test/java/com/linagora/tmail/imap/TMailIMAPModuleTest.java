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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.sasl.TMailPlainSaslMechanismFactory;

class TMailIMAPModuleTest {
    private final TMailIMAPModule testee = new TMailIMAPModule();

    @Test
    void provideDefaultImapSaslMechanismFactoriesShouldReplaceJamesPlainWithTMailPlain() {
        // GIVEN the TMail IMAP module is configured.
        // This lets TMail preserve user+delegate PLAIN behavior without applying it to SMTP.

        // WHEN the IMAP default SASL factory list is provided
        assertThat(testee.provideDefaultImapSaslMechanismFactories(
                new TMailPlainSaslMechanismFactory(),
                new OauthBearerSaslMechanismFactory(),
                new XOauth2SaslMechanismFactory()))

            // THEN only the PLAIN factory is replaced while OIDC defaults stay available
            .extracting(factory -> factory.getClass().getName())
            .containsExactly(
                TMailPlainSaslMechanismFactory.class.getName(),
                OauthBearerSaslMechanismFactory.class.getName(),
                XOauth2SaslMechanismFactory.class.getName());
    }
}
