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

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.POSTGRES;
import static org.apache.james.PostgresJamesConfiguration.EventBusImpl.IN_MEMORY;

import org.apache.james.ClockExtension;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.jmap.rfc8621.contract.EmailQueryMethodContract;
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbeModule;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.combined.identity.UsersRepositoryClassProbe;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.app.PostgresTmailConfiguration;
import com.linagora.tmail.james.app.PostgresTmailServer;
import com.linagora.tmail.james.common.LabelChangesMethodContract;
import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.firebase.FirebasePushClient;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailboxProbe;

public class PostgresEmailQueryMethodTest implements EmailQueryMethodContract {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<PostgresTmailConfiguration>(tmpDir ->
        PostgresTmailConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .implementation(POSTGRES)
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .mailbox(new MailboxConfiguration(false))
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.ENABLED)
            .eventBusImpl(IN_MEMORY)
            .build())
        .server(configuration -> PostgresTmailServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(UsersRepositoryClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TeamMailboxProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapGuiceContactAutocompleteProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(JmapSettingsProbe.class))
            .overrideWith(binder -> binder.bind(FirebasePushClient.class).toInstance(LabelChangesMethodContract.firebasePushClient()))
            .overrideWith(new DelegationProbeModule()))
        .extension(PostgresExtension.empty())
        .extension(new DockerOpenSearchExtension())
        .extension(new ClockExtension())
        .build();


    @Override
    @Test
    @Disabled("Flaky test. TODO stabilize it.")
    public void listMailsShouldBeSortedWhenUsingTo(GuiceJamesServer server) {
    }

    @Override
    @Test
    @Disabled("Flaky test. TODO stabilize it.")
    public void listMailsShouldBeSortedWhenUsingFrom(GuiceJamesServer server) {
    }

    @Override
    @Test
    @Disabled("Flaky test. TODO stabilize it.")
    public void inMailboxOtherThanShouldBeRejectedWhenInOperator(GuiceJamesServer server) {
    }
}
