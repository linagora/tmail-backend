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

import static com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsModule.PostgresJmapSettingsTable.SETTINGS;
import static com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsModule.PostgresJmapSettingsTable.STATE;
import static com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsModule.PostgresJmapSettingsTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.settings.PostgresJmapSettingsModule.PostgresJmapSettingsTable.USER;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.core.UuidState;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.postgres.extensions.types.Hstore;

import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresJmapSettingsDAO {
    public static class Factory {
        private final PostgresExecutor.Factory executorFactory;

        @Inject
        @Singleton
        public Factory(PostgresExecutor.Factory executorFactory) {
            this.executorFactory = executorFactory;
        }

        public PostgresJmapSettingsDAO create(Optional<Domain> domain) {
            return new PostgresJmapSettingsDAO(executorFactory.create(domain));
        }
    }

    private final PostgresExecutor postgresExecutor;

    public PostgresJmapSettingsDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<JmapSettings> getJmapSettings(Username username) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.selectFrom(TABLE_NAME)
            .where(USER.eq(username.asString()))))
            .map(this::toJmapSettings);
    }

    public Mono<UuidState> getState(Username username) {
        return postgresExecutor.executeRow(dslContext -> Mono.from(dslContext.select(STATE)
                .from(TABLE_NAME)
                .where(USER.eq(username.asString()))))
            .map(record -> new UuidState(record.get(STATE)));
    }

    public Mono<Void> saveSettings(Username username, UuidState newState, Map<JmapSettingsKey, JmapSettingsValue> newSettings) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.insertInto(TABLE_NAME)
            .set(USER, username.asString())
            .set(STATE, newState.value())
            .set(SETTINGS, toHstore(newSettings))));
    }

    public Mono<Void> updateFullSettings(Username username, UuidState newState, Map<JmapSettingsKey, JmapSettingsValue> newSettings) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(STATE, newState.value())
            .set(SETTINGS, toHstore(newSettings))
            .where(USER.eq(username.asString()))));
    }

    public Mono<Void> updateSettings(Username username,
                                     UuidState newState,
                                     Map<JmapSettingsKey, JmapSettingsValue> addSettings,
                                     List<JmapSettingsKey> removeSettings) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.update(TABLE_NAME)
            .set(STATE, newState.value())
            .set(DSL.field(SETTINGS.getName()),
                (Object) DSL.field("delete(" + SETTINGS.getName() + " || ?, ?)",
                    toHstore(addSettings),
                    removeSettings.stream().map(JmapSettingsKey::asString).toList().toArray(new String[0])))
            .where(USER.eq(username.asString()))));

    }

    public Mono<Void> deleteSettings(Username username) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.deleteFrom(TABLE_NAME)
            .where(USER.eq(username.asString()))));
    }

    private JmapSettings toJmapSettings(Record record) {
        return new JmapSettings(CollectionConverters.asScala(toMapSettingKeySettingValue(record.get(SETTINGS, LinkedHashMap.class))),
            new UuidState(record.get(STATE)));
    }

    private Map<JmapSettingsKey, JmapSettingsValue> toMapSettingKeySettingValue(Map<String, String> map) {
        return map.entrySet().stream().collect(ImmutableMap.toImmutableMap(entry -> JmapSettingsKey.liftOrThrow(entry.getKey()), entry -> new JmapSettingsValue(entry.getValue())));
    }

    private Hstore toHstore(Map<JmapSettingsKey, JmapSettingsValue> newSettings) {
        return Hstore.hstore(newSettings.entrySet()
            .stream()
            .collect(ImmutableMap.toImmutableMap(entry -> entry.getKey().asString(), entry -> entry.getValue().value())));
    }
}
