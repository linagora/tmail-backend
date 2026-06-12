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

import java.util.Arrays;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.modules.protocols.DefaultImapSaslMechanismClassNamesProvider;
import org.apache.james.modules.protocols.JamesDefaultImapSaslMechanismClassNamesProvider;
import org.apache.james.protocols.api.sasl.OauthBearerSaslMechanism;
import org.apache.james.protocols.api.sasl.XOauth2SaslMechanism;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class TMailDefaultImapSaslMechanismClassNamesProvider implements DefaultImapSaslMechanismClassNamesProvider {
    private static final ImmutableSet<String> TMAIL_IMAP_PACKAGES = ImmutableSet.of(
        TMailImapPackage.class.getCanonicalName(),
        TMailImapAuthPackage.class.getCanonicalName());

    private final JamesDefaultImapSaslMechanismClassNamesProvider jamesDefaultImapSaslMechanismClassNamesProvider;

    public TMailDefaultImapSaslMechanismClassNamesProvider() {
        this.jamesDefaultImapSaslMechanismClassNamesProvider = new JamesDefaultImapSaslMechanismClassNamesProvider();
    }

    @Override
    public ImmutableList<String> resolve(HierarchicalConfiguration<ImmutableNode> configuration) {
        if (containsTMailImapPackage(configuration)) {
            return ImmutableList.of(
                TMailPlainSaslMechanism.class.getCanonicalName(),
                OauthBearerSaslMechanism.class.getSimpleName(),
                XOauth2SaslMechanism.class.getSimpleName());
        }
        return jamesDefaultImapSaslMechanismClassNamesProvider.resolve(configuration);
    }

    private boolean containsTMailImapPackage(HierarchicalConfiguration<ImmutableNode> configuration) {
        return Arrays.stream(configuration.getStringArray("imapPackages"))
            .map(String::trim)
            .anyMatch(TMAIL_IMAP_PACKAGES::contains);
    }
}
