/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.james;

import org.apache.james.CassandraExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.rfc8621.contract.MailboxSetMethodContract;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.opendistro.DockerOpenDistroExtension;
import org.apache.james.mailbox.opendistro.DockerOpenDistroSingleton;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.driver.core.utils.UUIDs;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedMailboxSetMethodTest implements MailboxSetMethodContract {
    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig()
                    .disableSingleSave())
            .build())
        .extension(new DockerOpenDistroExtension(DockerOpenDistroSingleton.INSTANCE))
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule()))
        .build();

    @Override
    public MailboxId randomMailboxId() {
        return CassandraId.of(UUIDs.timeBased());
    }

    @Override
    public String errorInvalidMailboxIdMessage(String value) {
        return String.format("%s is not a mailboxId: Invalid UUID string: %s", value, value);
    }

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Mailbox/set call")
    public void newStateShouldBeUpToDate(GuiceJamesServer server) {}

    @Override
    @Test
    @Disabled("Distributed event bus is asynchronous, we cannot expect the newState to be returned immediately after Mailbox/set call")
    public void oldStateShouldIncludeSetChanges(GuiceJamesServer server) {}
}
