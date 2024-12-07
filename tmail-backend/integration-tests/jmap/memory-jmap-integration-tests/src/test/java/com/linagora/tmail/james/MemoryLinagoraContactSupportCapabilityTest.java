package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.io.File;

import org.apache.james.GuiceJamesServer;
import org.junit.jupiter.api.io.TempDir;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraContactSupportCapabilityContract;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import scala.collection.immutable.Map;
import scala.jdk.javaapi.CollectionConverters;

public class MemoryLinagoraContactSupportCapabilityTest implements LinagoraContactSupportCapabilityContract {

    @TempDir
    private File tmpDir;

    private GuiceJamesServer guiceJamesServer;

    @Override
    public GuiceJamesServer startJmapServer(Map<String, Object> overrideJmapProperties) {
        guiceJamesServer = MemoryServer.createServer(MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
                .build())
            .overrideWith(new LinagoraTestJMAPServerModule(CollectionConverters.asJava(overrideJmapProperties)));

        Throwing.runnable(() -> guiceJamesServer.start()).run();
        return guiceJamesServer;
    }

    @Override
    public void stopJmapServer() {
        if (guiceJamesServer != null && guiceJamesServer.isStarted()) {
            guiceJamesServer.stop();
        }
    }

}
