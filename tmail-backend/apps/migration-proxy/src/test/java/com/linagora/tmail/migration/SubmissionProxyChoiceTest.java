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


package com.linagora.tmail.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.utils.PropertiesProvider;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.migration.MigrationProxyServer.SubmissionProxyChoice;

class SubmissionProxyChoiceTest {
    @Test
    void shouldBeDisabledWhenNotConfigured() throws Exception {
        PropertiesProvider propertiesProvider = mock(PropertiesProvider.class);
        when(propertiesProvider.getConfiguration("migrationproxy")).thenReturn(new BaseConfiguration());

        assertThat(SubmissionProxyChoice.parse(propertiesProvider)).isEqualTo(SubmissionProxyChoice.DISABLED);
    }

    @Test
    void shouldBeDisabledWhenExplicitlyDisabled() throws Exception {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.addProperty("submission.enabled", "false");
        PropertiesProvider propertiesProvider = mock(PropertiesProvider.class);
        when(propertiesProvider.getConfiguration("migrationproxy")).thenReturn(configuration);

        assertThat(SubmissionProxyChoice.parse(propertiesProvider)).isEqualTo(SubmissionProxyChoice.DISABLED);
    }

    @Test
    void shouldBeEnabledWhenRequested() throws Exception {
        BaseConfiguration configuration = new BaseConfiguration();
        configuration.addProperty("submission.enabled", "true");
        PropertiesProvider propertiesProvider = mock(PropertiesProvider.class);
        when(propertiesProvider.getConfiguration("migrationproxy")).thenReturn(configuration);

        assertThat(SubmissionProxyChoice.parse(propertiesProvider)).isEqualTo(SubmissionProxyChoice.ENABLED);
    }

    @Test
    void shouldBeDisabledWithoutMigrationProxyProperties() throws Exception {
        PropertiesProvider propertiesProvider = mock(PropertiesProvider.class);
        when(propertiesProvider.getConfiguration("migrationproxy")).thenThrow(new FileNotFoundException());

        assertThat(SubmissionProxyChoice.parse(propertiesProvider)).isEqualTo(SubmissionProxyChoice.DISABLED);
    }

    @Test
    void shouldFailOnUnreadableMigrationProxyProperties() throws Exception {
        PropertiesProvider propertiesProvider = mock(PropertiesProvider.class);
        when(propertiesProvider.getConfiguration("migrationproxy")).thenThrow(new ConfigurationException("broken"));

        assertThatThrownBy(() -> SubmissionProxyChoice.parse(propertiesProvider))
            .isInstanceOf(RuntimeException.class);
    }
}
