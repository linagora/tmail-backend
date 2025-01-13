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

package com.linagora.tmail.encrypted;

import java.io.FileNotFoundException;
import java.util.Objects;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.utils.PropertiesProvider;

public class MailboxConfiguration {
    public static MailboxConfiguration parse(org.apache.james.server.core.configuration.Configuration configuration) throws ConfigurationException {
        PropertiesProvider propertiesProvider = new PropertiesProvider(new FileSystemImpl(configuration.directories()),
            configuration.configurationPath());

        return parse(propertiesProvider);
    }

    public static MailboxConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration("mailbox");
            return new MailboxConfiguration(configuration.getBoolean("gpg.encryption.enable", false));
        } catch (FileNotFoundException e) {
            return new MailboxConfiguration(false);
        }
    }

    private final boolean enableEncryption;

    public MailboxConfiguration(boolean enableEncryption) {
        this.enableEncryption = enableEncryption;
    }

    public boolean isEncryptionEnabled() {
        return enableEncryption;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(enableEncryption);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxConfiguration other) {
            return other.enableEncryption == this.enableEncryption;
        }
        return false;
    }
}
