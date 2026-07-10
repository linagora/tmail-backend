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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.protocols.smtp.core.esmtp.LoginSaslMechanismFactory;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.sasl.TMailPlainSaslMechanismFactory;

class TMailSMTPModuleTest {
    private final TMailSMTPModule testee = new TMailSMTPModule();

    @Test
    void provideDefaultSmtpSaslMechanismFactoriesShouldUseTMailPlainWithLoginFirst() {
        assertThat(testee.provideDefaultSmtpSaslMechanismFactories(
                new OauthBearerSaslMechanismFactory(),
                new XOauth2SaslMechanismFactory()))
            .extracting(factory -> factory.getClass().getName())
            .containsExactly(
                LoginSaslMechanismFactory.class.getName(),
                TMailPlainSaslMechanismFactory.class.getName(),
                OauthBearerSaslMechanismFactory.class.getName(),
                XOauth2SaslMechanismFactory.class.getName());
    }
}
