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

package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.DockerOpenPaasExtension;
import com.linagora.tmail.DockerOpenPaasSetup;
import com.linagora.tmail.OpenPaasModuleChooserConfiguration;
import com.linagora.tmail.OpenPaasUser;
import com.linagora.tmail.dav.DavClient;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraCalendarEventAttendanceGetMethodContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.calendar.ConfigurationPathFactory;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import net.fortuna.ical4j.model.Calendar;

public class MemoryLinagoraCalendarEventAttendanceGetMethodTest {

    static class WithoutOpenpaasTest implements LinagoraCalendarEventAttendanceGetMethodContract {
        @RegisterExtension
        static JamesServerExtension
            jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule(), new DelegationProbeModule()))
            .build();


        @Override
        public boolean supportFreeBusyQuery() {
            return false;
        }

        @Override
        public UserCredential bobCredential() {
            return new UserCredential(Fixture.BOB(), Fixture.BOB_PASSWORD());
        }

        @Override
        public UserCredential aliceCredential() {
            return new UserCredential(Fixture.ALICE(), Fixture.ALICE_PASSWORD());
        }

        @Override
        public UserCredential andreCredential() {
            return new UserCredential(Fixture.ANDRE(), Fixture.ANDRE_PASSWORD());
        }
    }

    static class WithOpenpaasTest implements LinagoraCalendarEventAttendanceGetMethodContract {
        @RegisterExtension
        @Order(1)
        static DockerOpenPaasExtension dockerOpenPaasExtension = new DockerOpenPaasExtension(DockerOpenPaasSetup.SINGLETON);

        @Order(2)
        @RegisterExtension
        static JamesServerExtension
            jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationPath(ConfigurationPathFactory.create(tmpDir).withCalendarSupport())
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .openPaasModuleChooserConfiguration(OpenPaasModuleChooserConfiguration.ENABLED_DAV)
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule(), new DelegationProbeModule())
                .overrideWith(dockerOpenPaasExtension.openpaasModule()))
            .build();

        private UserCredential bobCredential;
        private UserCredential aliceCredential;
        private UserCredential andreCredential;
        private DavClient davClient;

        @BeforeEach
        void setUp() throws Exception {
            davClient = new DavClient(dockerOpenPaasExtension.dockerOpenPaasSetup().davConfiguration());
        }

        private final Map<String, OpenPaasUser> davUsers = new HashMap<>();

        private UserCredential newTestUser() {
            OpenPaasUser openPaasUser = dockerOpenPaasExtension.newTestUser();
            davUsers.put(openPaasUser.email(), openPaasUser);
            return new UserCredential(Username.of(openPaasUser.email()), "secret");
        }

        @Override
        public boolean supportFreeBusyQuery() {
            return true;
        }

        @Override
        public UserCredential bobCredential() {
            if (bobCredential == null) {
                bobCredential = newTestUser();
            }
            return bobCredential;
        }

        @Override
        public UserCredential aliceCredential() {
            if (aliceCredential == null) {
                aliceCredential = newTestUser();
            }
            return aliceCredential;
        }

        @Override
        public UserCredential andreCredential() {
            if (andreCredential == null) {
                andreCredential = newTestUser();
            }
            return andreCredential;
        }

        @Override
        public void pushCalendarToDav(UserCredential userCredential, Calendar calendar, String eventUid) {
            String openPaasUserId = davUsers.get(userCredential.username().asString()).id();
            assertThat(openPaasUserId).isNotNull();
            URI davCalendarUri = URI.create("/calendars/" + openPaasUserId + "/" + openPaasUserId + "/" + eventUid + ".ics");
            davClient.createCalendar(userCredential.username().asString(), davCalendarUri, calendar).block();
        }
    }

}
