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

package com.linagora.tmail.james.jmap.settings;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.jmap.core.UuidState;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresJmapSettingsRepository implements JmapSettingsRepository {
    private PostgresJmapSettingsDAO.Factory postgresJmapSettingsDAOFactory;

    @Inject
    public PostgresJmapSettingsRepository(PostgresJmapSettingsDAO.Factory postgresJmapSettingsDAOFactory) {
        this.postgresJmapSettingsDAOFactory = postgresJmapSettingsDAOFactory;
    }

    @Override
    public Publisher<JmapSettings> get(Username username) {
        return postgresJmapSettingsDAOFactory.create(username.getDomainPart()).getJmapSettings(username);
    }

    @Override
    public Publisher<UuidState> getLatestState(Username username) {
        return postgresJmapSettingsDAOFactory.create(username.getDomainPart()).getState(username)
            .defaultIfEmpty(JmapSettingsStateFactory.INITIAL());
    }

    @Override
    public Publisher<SettingsStateUpdate> reset(Username username, JmapSettingsUpsertRequest settings) {
        PostgresJmapSettingsDAO jmapSettingsDAO = postgresJmapSettingsDAOFactory.create(username.getDomainPart());
        UuidState newState = JmapSettingsStateFactory.generateState();
        return jmapSettingsDAO.getState(username)
            .flatMap(oldState -> jmapSettingsDAO.updateFullSettings(username,
                    newState,
                    CollectionConverters.asJava(settings.settings()))
                .then(Mono.just(new SettingsStateUpdate(oldState, newState))))
            .switchIfEmpty(jmapSettingsDAO.saveSettings(username, newState, CollectionConverters.asJava(settings.settings()))
                .then(Mono.just(new SettingsStateUpdate(JmapSettingsStateFactory.INITIAL(), newState))));
    }

    @Override
    public Publisher<SettingsStateUpdate> updatePartial(Username username, JmapSettingsPatch settingsPatch) {
        Preconditions.checkArgument(!settingsPatch.isEmpty(), "Cannot update when upsert and remove is empty");
        Preconditions.checkArgument(!settingsPatch.isConflict(), "Cannot update and remove the same setting key");
        PostgresJmapSettingsDAO jmapSettingsDAO = postgresJmapSettingsDAOFactory.create(username.getDomainPart());
        UuidState newState = JmapSettingsStateFactory.generateState();
        return jmapSettingsDAO.getState(username)
            .flatMap(oldState -> jmapSettingsDAO.updateSettings(username,
                    newState,
                    CollectionConverters.asJava(settingsPatch.toUpsert().settings()),
                    CollectionConverters.asJava(settingsPatch.toRemove()))
                .then(Mono.just(new SettingsStateUpdate(oldState, newState))));
    }

    @Override
    public Publisher<Void> delete(Username username) {
        return postgresJmapSettingsDAOFactory.create(username.getDomainPart()).deleteSettings(username);
    }
}
