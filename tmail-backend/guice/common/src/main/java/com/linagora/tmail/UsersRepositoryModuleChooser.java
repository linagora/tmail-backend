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

package com.linagora.tmail;

import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.data.LdapUsersRepositoryModule;
import org.apache.james.server.core.configuration.FileConfigurationProvider;

import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.linagora.tmail.combined.identity.CombinedUsersRepositoryModule;

public class UsersRepositoryModuleChooser {
    public enum Implementation {
        LDAP,
        COMBINED,
        DEFAULT;

        public static Implementation parse(FileConfigurationProvider configurationProvider) {
            try {
                HierarchicalConfiguration<ImmutableNode> userRepositoryConfig = configurationProvider.getConfiguration("usersrepository");
                return Optional.ofNullable(userRepositoryConfig.getString("[@ldapHost]", null))
                    .map(anyHost -> userRepositoryConfig.getString("[@class]", "")
                        .contains("CombinedUsersRepository"))
                    .map(isCombined -> {
                        if (isCombined) {
                            return COMBINED;
                        }
                        return LDAP;
                    })
                    .orElse(DEFAULT);
            } catch (ConfigurationException e) {
                throw new RuntimeException("Error reading usersrepository.xml", e);
            }
        }
    }

    private final DatabaseCombinedUserRequireModule<?> databaseCombinedUserRequireModule;

    private final Module defaultModule;

    public UsersRepositoryModuleChooser(DatabaseCombinedUserRequireModule<?> databaseCombinedUserRequireModule, Module defaultModule) {
        this.databaseCombinedUserRequireModule = databaseCombinedUserRequireModule;
        this.defaultModule = defaultModule;
    }

    public Module chooseModule(Implementation implementation) {
        return switch (implementation) {
            case LDAP -> new LdapUsersRepositoryModule();
            case COMBINED -> Modules.override(defaultModule).with(Modules.combine(new CombinedUsersRepositoryModule(), databaseCombinedUserRequireModule));
            case DEFAULT -> defaultModule;
        };
    }
}
