/********************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 ********************************************************************/
package com.linagora.tmail.mailet.rag;

import com.google.inject.Injector;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RagConfigurationTest {
    private RagListenerConfiguration ragListenerConfiguration;
    private Injector  injector;
    private RagListener ragListener;

    @Test
    public void should_check_whiteList_NotNull() {
        Configuration configuration = new XMLConfiguration();
        configuration.addProperty("users", List.of("test1@localhost", "test2@localhost"));

        ragListenerConfiguration = RagListenerConfiguration.from(configuration);
        RagListenerConfiguration expected = new RagListenerConfiguration(
                Optional.of(List.of("test1@localhost", "test2@localhost")));

        assertThat(ragListenerConfiguration.getWhitelist())
                .isEqualTo(expected.getWhitelist());
    }

    @Test
    public void should_check_whiteList_Null() {
        Configuration configuration = new XMLConfiguration();
        configuration.addProperty("users", null);

        ragListenerConfiguration = RagListenerConfiguration.from(configuration);
        RagListenerConfiguration expected = new RagListenerConfiguration(
            Optional.empty());

        assertThat(ragListenerConfiguration.getWhitelist())
            .isEqualTo(expected.getWhitelist());
    }
}
