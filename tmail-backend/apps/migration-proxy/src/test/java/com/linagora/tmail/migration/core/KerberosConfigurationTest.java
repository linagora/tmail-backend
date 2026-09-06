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

package com.linagora.tmail.migration.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.junit.jupiter.api.Test;

/**
 * The properties of the proxy are rendered back into the server configuration James reads its
 * {@code auth.gssapi} settings from: this is what that rendering has to look like.
 */
class KerberosConfigurationTest {
    private static KerberosConfiguration kerberos(Map<String, String> realmMapping) {
        return new KerberosConfiguration("imap", "imap.domain.tld", "imap/imap.domain.tld@DOMAIN.TLD",
            "/root/conf/imap.keytab", true, realmMapping);
    }

    @Test
    void asServerConfigurationShouldRenderTheGssapiSettings() {
        HierarchicalConfiguration<ImmutableNode> configuration = kerberos(Map.of()).asServerConfiguration();

        assertThat(configuration.getString("auth.gssapi.serviceName")).isEqualTo("imap");
        assertThat(configuration.getString("auth.gssapi.serverName")).isEqualTo("imap.domain.tld");
        assertThat(configuration.getString("auth.gssapi.principal")).isEqualTo("imap/imap.domain.tld@DOMAIN.TLD");
        assertThat(configuration.getString("auth.gssapi.keyTab")).isEqualTo("/root/conf/imap.keytab");
        assertThat(configuration.getBoolean("auth.requireSSL")).isTrue();
    }

    @Test
    void asServerConfigurationShouldNotRenderAnAbsentRealmMapping() {
        assertThat(kerberos(Map.of()).asServerConfiguration().immutableConfigurationsAt("auth.gssapi.realmMapping"))
            .isEmpty();
    }

    @Test
    void asServerConfigurationShouldRenderARealmOfTheMappingPerNode() {
        Map<String, String> realmMapping = new LinkedHashMap<>();
        realmMapping.put("DOMAIN.TLD", "domain.tld");
        realmMapping.put("SUB.DOMAIN.TLD", "sub.domain.tld");

        assertThat(kerberos(realmMapping).asServerConfiguration().configurationsAt("auth.gssapi.realmMapping.realm"))
            .extracting(realm -> realm.getString("[@name]"), realm -> realm.getString("[@domain]"))
            .containsExactly(tuple("DOMAIN.TLD", "domain.tld"), tuple("SUB.DOMAIN.TLD", "sub.domain.tld"));
    }
}
