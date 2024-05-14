package com.linagora.tmail.james;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.transport.mailets.amqp.AmqpExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.james.common.LinagoraCalendarEventReplyWithAQMPWorkflowContract;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class MemoryCalendarEventReplyWithAQMPWorkflowTest implements LinagoraCalendarEventReplyWithAQMPWorkflowContract {
    static final String AQMP_EXCHANGE_NAME = "james:events";
    static final String AQMP_ROUTING_KEY = "icalendar_routing_key2";

    @RegisterExtension
    public static AmqpExtension amqpExtension = new AmqpExtension(AQMP_EXCHANGE_NAME, AQMP_ROUTING_KEY);

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(
        tmpDir -> {
            // Copy all resources to the james configuration folder
            Path confPath = tmpDir.toPath().resolve("conf");
            Throwing.runnable(() -> FileUtils.copyDirectory(new File(ClassLoader.getSystemClassLoader().getResource(".").getFile()),
                confPath.toFile())).run();

            // Replace amqp uri in the mailetcontainer_with_aqmpforward_openpass.xml and write it to mailetcontainer.xml
            Throwing.runnable(() -> {
                String sourceContent = FileUtils.readFileToString(confPath.resolve("mailetcontainer_with_aqmpforward_openpass.xml").toFile(), StandardCharsets.UTF_8);
                String newContent = sourceContent.replace("{{AmqpForwardAttribute_uri}}", amqpExtension.getAmqpUri());
                FileUtils.writeStringToFile(confPath.resolve("mailetcontainer.xml").toFile(), newContent, StandardCharsets.UTF_8);
            }).run();

            System.out.println("confPath: " + confPath);
            return MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .usersRepository(DEFAULT)
                .build();
        })
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule(), new DelegationProbeModule()))
        .build();

    @Override
    public String randomBlobId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public Optional<String> readAQMPContent() {
        return Throwing.supplier(() -> amqpExtension.readContent()).get();
    }
}
