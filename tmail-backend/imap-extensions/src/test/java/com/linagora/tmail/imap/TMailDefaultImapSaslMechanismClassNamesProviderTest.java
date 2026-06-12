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

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.protocols.api.sasl.OauthBearerSaslMechanism;
import org.apache.james.protocols.api.sasl.PlainSaslMechanism;
import org.apache.james.protocols.api.sasl.XOauth2SaslMechanism;
import org.junit.jupiter.api.Test;

class TMailDefaultImapSaslMechanismClassNamesProviderTest {
    private final TMailDefaultImapSaslMechanismClassNamesProvider testee = new TMailDefaultImapSaslMechanismClassNamesProvider();

    @Test
    void resolveShouldReturnTMailPlainWhenTMailImapPackageConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("imapPackages", TMailImapPackage.class.getCanonicalName());

        assertThat(testee.resolve(configuration))
            .containsExactly(
                TMailPlainSaslMechanism.class.getCanonicalName(),
                OauthBearerSaslMechanism.class.getSimpleName(),
                XOauth2SaslMechanism.class.getSimpleName());
    }

    @Test
    void resolveShouldReturnTMailPlainWhenTMailImapAuthPackageConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("imapPackages", TMailImapAuthPackage.class.getCanonicalName());

        assertThat(testee.resolve(configuration))
            .containsExactly(
                TMailPlainSaslMechanism.class.getCanonicalName(),
                OauthBearerSaslMechanism.class.getSimpleName(),
                XOauth2SaslMechanism.class.getSimpleName());
    }

    @Test
    void resolveShouldTrimConfiguredPackages() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("imapPackages", " " + TMailImapPackage.class.getCanonicalName() + " ");

        assertThat(testee.resolve(configuration))
            .containsExactly(
                TMailPlainSaslMechanism.class.getCanonicalName(),
                OauthBearerSaslMechanism.class.getSimpleName(),
                XOauth2SaslMechanism.class.getSimpleName());
    }

    @Test
    void resolveShouldReturnJamesDefaultsWhenNoTMailPackageConfigured() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();

        assertThat(testee.resolve(configuration))
            .containsExactly(
                PlainSaslMechanism.class.getSimpleName(),
                OauthBearerSaslMechanism.class.getSimpleName(),
                XOauth2SaslMechanism.class.getSimpleName());
    }
}
