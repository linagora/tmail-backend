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

package com.linagora.tmail.james.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;

class ShippedUsersRepositoryConfigurationTest {
    private static final File SHIPPED_USERS_REPOSITORY = new File("src/main/conf/usersrepository.xml");

    @Test
    void shippedUsersRepositoryShouldHashPasswordsWithPBKDF2() throws ConfigurationException {
        XMLConfiguration configuration = new Configurations().xml(SHIPPED_USERS_REPOSITORY);

        assertThat(configuration.getString("algorithm")).startsWith("PBKDF2");
    }
}
