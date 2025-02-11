package com.linagora.tmail.james;

import scala.collection.immutable.Seq;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.calendar.getattendance.LinagoraCalendarEventAttendanceGetMethodContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryLinagoraCalendarEventAttendanceGetMethodTest implements
    LinagoraCalendarEventAttendanceGetMethodContract {

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
    public Seq<String> _sendInvitationEmailToBobAndGetIcsBlobIds(GuiceJamesServer server,
                                                                 String invitationEml,
                                                                 Seq<String> icsPartIds) {
        return null;
    }
}
