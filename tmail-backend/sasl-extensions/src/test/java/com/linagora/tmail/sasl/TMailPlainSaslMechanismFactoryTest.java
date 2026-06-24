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

package com.linagora.tmail.sasl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;
import org.junit.jupiter.api.Test;

class TMailPlainSaslMechanismFactoryTest {
    private final TMailPlainSaslMechanismFactory testee = new TMailPlainSaslMechanismFactory();

    @Test
    void createShouldReturnTMailPlainWhenTMailImapPackageIsConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("imapPackages", "com.linagora.tmail.imap.TMailImapPackage");

        assertThat(testee.create(configuration))
            .isInstanceOf(TMailPlainSaslMechanism.class);
    }

    @Test
    void createShouldReturnTMailPlainWhenTMailImapAuthPackageIsConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("imapPackages", "com.linagora.tmail.imap.TMailImapAuthPackage");

        assertThat(testee.create(configuration))
            .isInstanceOf(TMailPlainSaslMechanism.class);
    }

    @Test
    void createShouldReturnTMailPlainWhenTMailSmtpHandlerPackageIsConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("handlerchain[@coreHandlersPackage]", "com.linagora.tmail.smtp.TMailCmdHandlerLoader");

        assertThat(testee.create(configuration))
            .isInstanceOf(TMailPlainSaslMechanism.class);
    }

    @Test
    void createShouldReturnJamesPlainWhenNoTMailProtocolPackageIsConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        assertThat(testee.create(configuration))
            .isInstanceOf(PlainSaslMechanism.class)
            .isNotInstanceOf(TMailPlainSaslMechanism.class);
    }

    @Test
    void createShouldKeepSmtpRequireSslDefaultToFalse() {
        TMailPlainSaslMechanismFactory testee = new TMailPlainSaslMechanismFactory(false);
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("handlerchain[@coreHandlersPackage]", "com.linagora.tmail.smtp.TMailCmdHandlerLoader");

        assertThat(testee.create(configuration).isAvailableOnTransport(false))
            .isTrue();
    }
}
