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

import java.util.List;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismFactory;
import org.apache.james.protocols.sasl.OauthBearerSaslMechanismFactory;
import org.apache.james.protocols.sasl.XOauth2SaslMechanismFactory;
import org.apache.james.protocols.smtp.core.esmtp.LoginSaslMechanismFactory;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.sasl.TMailPlainSaslMechanismFactory;

class TMailSMTPModuleTest {
    private static final boolean CLEAR_TEXT = false;
    private static final boolean ENCRYPTED = true;
    private static final int DEFAULT_MECHANISM_COUNT = 4;

    private final TMailSMTPModule testee = new TMailSMTPModule();

    @Test
    void provideDefaultSmtpSaslMechanismFactoriesShouldUseTMailPlainWithLoginFirst() {
        assertThat(testee.provideDefaultSmtpSaslMechanismFactories())
            .extracting(factory -> factory.getClass().getName())
            .containsExactly(
                LoginSaslMechanismFactory.class.getName(),
                TMailPlainSaslMechanismFactory.class.getName(),
                OauthBearerSaslMechanismFactory.class.getName(),
                XOauth2SaslMechanismFactory.class.getName());
    }

    @Test
    void defaultMechanismsShouldNotBeAvailableOnClearTextTransportWhenRequireSSLIsUnset() {
        assertThat(defaultMechanisms(oidcConfiguration()))
            .hasSize(DEFAULT_MECHANISM_COUNT)
            .noneMatch(mechanism -> mechanism.isAvailableOnTransport(CLEAR_TEXT));
    }

    @Test
    void defaultMechanismsShouldBeAvailableOnEncryptedTransportWhenRequireSSLIsUnset() {
        assertThat(defaultMechanisms(oidcConfiguration()))
            .hasSize(DEFAULT_MECHANISM_COUNT)
            .allMatch(mechanism -> mechanism.isAvailableOnTransport(ENCRYPTED));
    }

    @Test
    void defaultMechanismsShouldBeAvailableOnClearTextTransportWhenRequireSSLIsFalse() {
        HierarchicalConfiguration<ImmutableNode> configuration = oidcConfiguration();
        configuration.addProperty("auth.requireSSL", false);

        assertThat(defaultMechanisms(configuration))
            .hasSize(DEFAULT_MECHANISM_COUNT)
            .allMatch(mechanism -> mechanism.isAvailableOnTransport(CLEAR_TEXT));
    }

    private List<SaslMechanism> defaultMechanisms(HierarchicalConfiguration<ImmutableNode> configuration) {
        return testee.provideDefaultSmtpSaslMechanismFactories()
            .stream()
            .map(factory -> create(factory, configuration))
            .toList();
    }

    private static SaslMechanism create(SaslMechanismFactory factory, HierarchicalConfiguration<ImmutableNode> configuration) {
        try {
            return factory.create(configuration);
        } catch (ConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HierarchicalConfiguration<ImmutableNode> oidcConfiguration() {
        BaseHierarchicalConfiguration configuration = new BaseHierarchicalConfiguration();
        configuration.addProperty("auth.plainAuthEnabled", true);
        configuration.addProperty("auth.oidc.jwksURL", "http://127.0.0.1/jwks");
        configuration.addProperty("auth.oidc.claim", "email_address");
        configuration.addProperty("auth.oidc.oidcConfigurationURL", "https://example.com/.well-known/openid-configuration");
        configuration.addProperty("auth.oidc.scope", "openid");
        configuration.addProperty("auth.oidc.aud", "tmail");
        return configuration;
    }
}
