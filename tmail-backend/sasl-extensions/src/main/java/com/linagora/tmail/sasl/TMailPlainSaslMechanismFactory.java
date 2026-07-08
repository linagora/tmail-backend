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

import java.util.Arrays;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.sasl.PlainSaslMechanismFactory;
import org.apache.james.protocols.sasl.plain.PlainSaslMechanism;

import com.google.common.collect.ImmutableSet;

public class TMailPlainSaslMechanismFactory extends PlainSaslMechanismFactory {
    private static final ImmutableSet<String> TMAIL_IMAP_PACKAGES = ImmutableSet.of(
        "com.linagora.tmail.imap.TMailImapPackage",
        "com.linagora.tmail.imap.TMailImapAuthPackage");
    private static final String TMAIL_SMTP_HANDLER_PACKAGE = "com.linagora.tmail.smtp.TMailCmdHandlerLoader";

    @Override
    public SaslMechanism create(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        if (usesTMailImapPackage(serverConfiguration) || usesTMailSmtpHandlerPackage(serverConfiguration)) {
            return new TMailPlainSaslMechanism(plainAuthEnabled(serverConfiguration), requiresSsl(serverConfiguration));
        }
        return new PlainSaslMechanism(plainAuthEnabled(serverConfiguration), requiresSsl(serverConfiguration));
    }

    private boolean usesTMailImapPackage(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        return Arrays.stream(serverConfiguration.getStringArray("imapPackages"))
            .map(String::trim)
            .anyMatch(TMAIL_IMAP_PACKAGES::contains);
    }

    private boolean usesTMailSmtpHandlerPackage(HierarchicalConfiguration<ImmutableNode> serverConfiguration) {
        return TMAIL_SMTP_HANDLER_PACKAGE.equals(serverConfiguration.getString("handlerchain[@coreHandlersPackage]", ""));
    }
}
